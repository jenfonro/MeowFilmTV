package tv.meowfilm.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class ServerPlayHistoryItem(
    val contentKey: String,
    val siteKey: String,
    val siteName: String,
    val spiderApi: String,
    val videoId: String,
    val videoTitle: String,
    val videoPoster: String,
    val videoRemark: String,
    val playFlag: String,
    val episodeIndex: Int,
    val episodeName: String,
    val updatedAtSec: Long,
)

object MeowFilmHistoryClient {
    fun fetchPlayHistory(
        serverUrl: String,
        session: MeowFilmApiClient.Session,
        limit: Int = 50,
    ): List<ServerPlayHistoryItem> {
        val base = serverUrl.trim().removeSuffix("/")
        if (base.isBlank()) throw IllegalStateException("未设置服务器地址")
        val url = "$base/api/playhistory?limit=${limit.coerceIn(1, 50)}"
        val resp = HttpClient.get(url = url, headers = mapOf("Cookie" to session.cookie, "Accept" to "application/json"))
        if (resp.code !in 200..299) throw IllegalStateException("请求失败（HTTP ${resp.code}）")
        val arr = JSONArray(String(resp.body, Charsets.UTF_8))
        val out = ArrayList<ServerPlayHistoryItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val siteKey = o.optString("siteKey").orEmpty().trim()
            val spiderApi = o.optString("spiderApi").orEmpty().trim()
            val videoId = o.optString("videoId").orEmpty().trim()
            val title = o.optString("videoTitle").orEmpty().trim()
            if (siteKey.isBlank() || spiderApi.isBlank() || videoId.isBlank() || title.isBlank()) continue
            out +=
                ServerPlayHistoryItem(
                    contentKey = o.optString("contentKey").orEmpty().trim(),
                    siteKey = siteKey,
                    siteName = o.optString("siteName").orEmpty().trim(),
                    spiderApi = spiderApi,
                    videoId = videoId,
                    videoTitle = title,
                    videoPoster = o.optString("videoPoster").orEmpty().trim(),
                    videoRemark = o.optString("videoRemark").orEmpty().trim(),
                    playFlag = o.optString("playFlag").orEmpty().trim(),
                    episodeIndex = o.optInt("episodeIndex", 0).coerceAtLeast(0),
                    episodeName = o.optString("episodeName").orEmpty().trim(),
                    updatedAtSec = o.optLong("updatedAt", 0L).coerceAtLeast(0L),
                )
        }
        return out
    }

    fun postPlayHistory(
        serverUrl: String,
        session: MeowFilmApiClient.Session,
        payload: JSONObject,
    ) {
        val base = serverUrl.trim().removeSuffix("/")
        if (base.isBlank()) throw IllegalStateException("未设置服务器地址")
        val url = "$base/api/playhistory"
        val resp =
            HttpClient.postJson(
                url = url,
                jsonBody = payload.toString(),
                headers = mapOf("Cookie" to session.cookie, "Accept" to "application/json"),
            )
        if (resp.code !in 200..299) {
            val msg =
                runCatching { JSONObject(String(resp.body, Charsets.UTF_8)).optString("message").trim() }
                    .getOrDefault("")
            throw IllegalStateException(if (msg.isNotBlank()) msg else "请求失败（HTTP ${resp.code}）")
        }
        val obj = runCatching { JSONObject(String(resp.body, Charsets.UTF_8)) }.getOrNull()
        if (obj != null && obj.has("success") && !obj.optBoolean("success", true)) {
            throw IllegalStateException(obj.optString("message").ifBlank { "请求失败" })
        }
    }
}

