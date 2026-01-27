package tv.meowfilm.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class MeowFilmUser(
    val username: String,
    val role: String,
)

data class MeowFilmBootstrap(
    val authenticated: Boolean,
    val siteName: String,
    val user: MeowFilmUser?,
    val settings: MeowFilmServerSettings,
)

data class MeowFilmServerSettings(
    val userCatPawOpenApiBase: String,
    val userCatPawOpenApiKey: String,
    val userCatPawOpenProxy: String,
    val catPawOpenApiBase: String,
    val searchThreadCount: Int,
    val searchSiteOrder: List<String>,
    val searchCoverSite: String,
    val magicEpisodeRules: List<String>,
    val magicEpisodeCleanRegexRules: List<String>,
    val magicAggregateRegexRules: List<String>,
)

data class MeowFilmSite(
    val key: String,
    val name: String,
    val api: String,
    val enabled: Boolean,
    val search: Boolean,
)

object MeowFilmApiClient {
    private const val COOKIE_NAME = "meowfilm_auth"

    data class Session(
        val token: String,
        val cookie: String,
    )

    fun login(serverUrl: String, username: String, password: String): Session {
        val base = normalizeBase(serverUrl)
        if (base.isBlank()) throw IllegalStateException("未设置服务器地址")
        val u = username.trim()
        val p = password
        if (u.isBlank() || p.isBlank()) throw IllegalStateException("未设置账号或密码")

        val url = "$base/api/login"
        val body = JSONObject().put("username", u).put("password", p).toString()
        val resp = HttpClient.postJson(url = url, jsonBody = body, headers = mapOf("Accept" to "application/json"))
        if (resp.code !in 200..299) {
            val msg = runCatching {
                JSONObject(String(resp.body, Charsets.UTF_8)).optString("message").ifBlank { "" }
            }.getOrDefault("")
            throw IllegalStateException(if (msg.isNotBlank()) msg else "登录失败（HTTP ${resp.code}）")
        }

        val cookies = resp.headers("Set-Cookie")
        val token = cookies.firstNotNullOfOrNull { parseCookieValue(it, COOKIE_NAME) }.orEmpty()
        if (token.isBlank()) throw IllegalStateException("登录失败：未返回 Cookie")
        return Session(token = token, cookie = "$COOKIE_NAME=$token")
    }

    fun sessionFromToken(token: String): Session {
        val t = token.trim()
        if (t.isBlank()) throw IllegalStateException("token 为空")
        return Session(token = t, cookie = "$COOKIE_NAME=$t")
    }

    fun bootstrap(serverUrl: String, session: Session, page: String = "index"): MeowFilmBootstrap {
        val base = normalizeBase(serverUrl)
        if (base.isBlank()) throw IllegalStateException("未设置服务器地址")
        val q = URLEncoder.encode(page, "UTF-8")
        val url = "$base/api/bootstrap?page=$q"
        val resp =
            HttpClient.get(
                url = url,
                headers = mapOf("Cookie" to session.cookie, "Accept" to "application/json"),
            )
        if (resp.code !in 200..299) throw IllegalStateException("请求失败（HTTP ${resp.code}）")
        val root = JSONObject(String(resp.body, Charsets.UTF_8))
        val authenticated = root.optBoolean("authenticated", false)
        val siteName = root.optString("siteName").orEmpty()
        val userObj = root.optJSONObject("user")
        val user =
            if (userObj != null) {
                MeowFilmUser(
                    username = userObj.optString("username").orEmpty(),
                    role = userObj.optString("role").orEmpty(),
                )
            } else {
                null
            }
        val settingsObj = root.optJSONObject("settings") ?: JSONObject()
        return MeowFilmBootstrap(
            authenticated = authenticated,
            siteName = siteName,
            user = user,
            settings = parseSettings(settingsObj),
        )
    }

    fun userSites(serverUrl: String, session: Session): List<MeowFilmSite> {
        val base = normalizeBase(serverUrl)
        if (base.isBlank()) throw IllegalStateException("未设置服务器地址")
        val url = "$base/api/user/sites"
        val resp =
            HttpClient.get(
                url = url,
                headers = mapOf("Cookie" to session.cookie, "Accept" to "application/json"),
            )
        if (resp.code !in 200..299) throw IllegalStateException("请求失败（HTTP ${resp.code}）")
        val root = JSONObject(String(resp.body, Charsets.UTF_8))
        val ok = root.optBoolean("success", false)
        if (!ok) throw IllegalStateException(root.optString("message").ifBlank { "请求失败" })
        val arr = root.optJSONArray("sites") ?: JSONArray()
        val out = ArrayList<MeowFilmSite>(arr.length())
        for (i in 0 until arr.length()) {
            val it = arr.optJSONObject(i) ?: continue
            val api = it.optString("api").orEmpty()
            val key = it.optString("key").orEmpty()
            if (key.isBlank() || api.isBlank()) continue
            out +=
                MeowFilmSite(
                    key = key,
                    name = it.optString("name").orEmpty().ifBlank { key },
                    api = api,
                    enabled = it.optBoolean("enabled", true),
                    search = it.optBoolean("search", true),
                )
        }
        return out
    }

    private fun parseSettings(o: JSONObject): MeowFilmServerSettings {
        fun str(k: String) = o.optString(k).orEmpty().trim()
        fun strList(k: String): List<String> {
            val arr = o.optJSONArray(k) ?: return emptyList()
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotBlank()) out += s
            }
            return out
        }
        val thread = o.optInt("searchThreadCount", 5).coerceIn(1, 50)
        return MeowFilmServerSettings(
            userCatPawOpenApiBase = str("userCatPawOpenApiBase"),
            userCatPawOpenApiKey = str("userCatPawOpenApiKey"),
            userCatPawOpenProxy = str("userCatPawOpenProxy"),
            catPawOpenApiBase = str("catPawOpenApiBase"),
            searchThreadCount = thread,
            searchSiteOrder = strList("searchSiteOrder"),
            searchCoverSite = str("searchCoverSite"),
            magicEpisodeRules = strList("magicEpisodeRules"),
            magicEpisodeCleanRegexRules = strList("magicEpisodeCleanRegexRules"),
            magicAggregateRegexRules = strList("magicAggregateRegexRules"),
        )
    }

    private fun parseCookieValue(setCookie: String, name: String): String? {
        val raw = setCookie.trim()
        if (raw.isEmpty()) return null
        val parts = raw.split(';')
        val kv = parts.firstOrNull().orEmpty()
        val idx = kv.indexOf('=')
        if (idx <= 0) return null
        val k = kv.substring(0, idx).trim()
        if (!k.equals(name, ignoreCase = true)) return null
        return kv.substring(idx + 1).trim().ifBlank { null }
    }

    private fun normalizeBase(input: String): String {
        val raw = input.trim().removeSuffix("/")
        if (raw.isBlank()) return ""
        return raw
    }
}
