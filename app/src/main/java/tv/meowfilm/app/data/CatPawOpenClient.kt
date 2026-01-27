package tv.meowfilm.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

data class CatSearchItem(
    val siteKey: String,
    val siteName: String,
    val spiderApi: String,
    val videoId: String,
    val title: String,
    val poster: String,
    val remark: String,
)

data class CatDetail(
    val title: String,
    val poster: String,
    val year: String,
    val type: String,
    val remark: String,
    val content: String,
    val playFrom: String,
    val playUrl: String,
)

data class CatPlayResult(
    val url: String,
    val headers: Map<String, String>,
)

object CatPawOpenClient {
    fun search(
        apiBase: String,
        tvUser: String,
        site: MeowFilmSite,
        keyword: String,
        page: Int = 1,
    ): List<CatSearchItem> {
        val q = keyword.trim()
        if (q.isBlank()) return emptyList()
        val url = buildSpiderUrl(apiBase, site.api, "search")
        val payload = JSONObject().put("wd", q).put("page", page).toString()
        val resp =
            HttpClient.postJson(
                url = url,
                jsonBody = payload,
                headers =
                    mapOf(
                        "Accept" to "application/json",
                    ),
            )
        if (resp.code !in 200..299) throw IllegalStateException("HTTP ${resp.code}")
        val root = JSONObject(String(resp.body, Charsets.UTF_8))
        val list = root.optJSONArray("list") ?: JSONArray()
        val out = ArrayList<CatSearchItem>(list.length())
        for (i in 0 until list.length()) {
            val it = list.optJSONObject(i) ?: continue
            val id = optAnyString(it, "vod_id", "id").trim()
            val name = optAnyString(it, "vod_name", "name", "title").trim()
            if (id.isBlank() || name.isBlank()) continue
            val pic = optAnyString(it, "vod_pic", "pic", "poster").trim()
            val remark = optAnyString(it, "vod_remarks", "remark").trim()
            out +=
                CatSearchItem(
                    siteKey = site.key,
                    siteName = site.name,
                    spiderApi = site.api,
                    videoId = id,
                    title = name,
                    poster = pic,
                    remark = remark,
                )
        }
        return out
    }

    fun detail(
        apiBase: String,
        tvUser: String,
        spiderApi: String,
        videoId: String,
    ): CatDetail {
        val url = buildSpiderUrl(apiBase, spiderApi, "detail")
        val payload = JSONObject().put("id", videoId.trim()).toString()
        val resp =
            HttpClient.postJson(
                url = url,
                jsonBody = payload,
                headers =
                    mapOf(
                        "Accept" to "application/json",
                    ),
            )
        if (resp.code !in 200..299) throw IllegalStateException("HTTP ${resp.code}")
        val root = JSONObject(String(resp.body, Charsets.UTF_8))
        val list = root.optJSONArray("list")
        val obj = list?.optJSONObject(0) ?: root.optJSONObject("data") ?: root
        fun get(vararg keys: String): String = optAnyString(obj, *keys).trim()
        return CatDetail(
            title = get("vod_name", "name", "title"),
            poster = get("vod_pic", "pic", "poster"),
            year = get("vod_year", "year"),
            type = get("vod_class", "vod_type", "type_name", "type"),
            remark = get("vod_remarks", "remark"),
            content = get("vod_content", "content", "desc"),
            playFrom = get("vod_play_from", "play_from", "vod_playfrom", "vod_play_froms"),
            playUrl = get("vod_play_url", "play_url", "vod_playurl", "vod_play_urls"),
        )
    }

    fun play(
        apiBase: String,
        tvUser: String,
        spiderApi: String,
        flag: String,
        id: String,
        extraQuery: Map<String, String> = emptyMap(),
    ): CatPlayResult {
        val url = buildPlayUrl(apiBase, extraQuery)
        val payload =
            JSONObject()
                .put("flag", flag)
                .put("id", id)
                // Let CatPawOpen route the play request to either built-in pan resolvers or the correct runtime.
                .put("siteApi", spiderApi.trim())
                .toString()
        val resp =
            HttpClient.postJson(
                url = url,
                jsonBody = payload,
                headers =
                    mapOf(
                        "Accept" to "application/json",
                        "X-TV-User" to tvUser.trim(),
                    ),
            )
        if (resp.code !in 200..299) throw IllegalStateException("HTTP ${resp.code}")
        val root = JSONObject(String(resp.body, Charsets.UTF_8))
        val dataObj = root.optJSONObject("data")
        val urlStr =
            root.optString("url").orEmpty().trim().ifBlank {
                dataObj?.optString("url").orEmpty().trim()
            }
        val headerObj =
            root.optJSONObject("header")
                ?: root.optJSONObject("headers")
                ?: dataObj?.optJSONObject("header")
                ?: dataObj?.optJSONObject("headers")
                ?: JSONObject()
        val headers = mutableMapOf<String, String>()
        headerObj.keys().forEach { k ->
            val v = headerObj.optString(k).orEmpty()
            if (k.isNotBlank() && v.isNotBlank()) headers[k] = v
        }
        return CatPlayResult(url = urlStr, headers = headers)
    }

    private fun buildPlayUrl(
        apiBase: String,
        query: Map<String, String> = emptyMap(),
    ): String {
        val base = normalizeCatApiBase(apiBase)
        if (base.isBlank()) throw IllegalStateException("CatPawOpen 接口地址未设置")
        val u = URL(base)
        val target = URL(u, "play")
        if (query.isEmpty()) return target.toString()
        val b = StringBuilder(target.toString())
        if (!b.contains("?")) b.append("?") else b.append("&")
        query.entries.forEachIndexed { idx, (k, v) ->
            if (idx > 0) b.append("&")
            b.append(URLEncoder.encode(k, "UTF-8")).append("=").append(URLEncoder.encode(v, "UTF-8"))
        }
        return b.toString()
    }

    private fun buildSpiderUrl(
        apiBase: String,
        spiderApi: String,
        action: String,
        query: Map<String, String> = emptyMap(),
    ): String {
        val base = normalizeCatApiBase(apiBase)
        if (base.isBlank()) throw IllegalStateException("CatPawOpen 接口地址未设置")
        val spiderPath = spiderApi.trim().removeSuffix("/")
        if (!isSpiderApiPath(spiderPath)) throw IllegalStateException("站点 API 无效")
        val u = URL(base)
        val target = URL(u, "$spiderPath/${action.trim()}")
        if (query.isEmpty()) return target.toString()
        val b = StringBuilder(target.toString())
        if (!b.contains("?")) b.append("?") else b.append("&")
        query.entries.forEachIndexed { idx, (k, v) ->
            if (idx > 0) b.append("&")
            b.append(URLEncoder.encode(k, "UTF-8")).append("=").append(URLEncoder.encode(v, "UTF-8"))
        }
        return b.toString()
    }

    private fun normalizeCatApiBase(inputUrl: String): String {
        val raw = inputUrl.trim()
        if (raw.isBlank()) return ""
        return try {
            val url = URL(raw)
            var path = url.path.ifBlank { "/" }
            val spiderIdx = path.indexOf("/spider/")
            if (spiderIdx >= 0) path = path.substring(0, spiderIdx).ifBlank { "/" }
            // If the input is an id-prefixed spider base like "/<id>/spider/...", drop the id segment.
            if (Regex("^/[a-f0-9]{10}/?$").matches(path)) path = "/"
            path = path.replace(Regex("/spider/?$"), "/")
            path = path.replace(Regex("/(full-config|config|website)/?$"), "/")
            if (!path.endsWith("/")) path += "/"
            URL(url.protocol, url.host, url.port, path).toString()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun isSpiderApiPath(path: String): Boolean {
        val p = path.trim()
        // New CatPawOpen requires all runtime spiders be accessed via an explicit id prefix.
        return Regex("^/[a-f0-9]{10}/spider/").containsMatchIn(p)
    }

    private fun optAnyString(obj: JSONObject, vararg keys: String): String {
        keys.forEach { k ->
            if (k.isBlank()) return@forEach
            val v = obj.opt(k)
            if (v == null) return@forEach
            val s = when (v) {
                is String -> v
                is Number -> v.toString()
                else -> v.toString()
            }
            if (s.isNotBlank()) return s
        }
        return ""
    }
}
