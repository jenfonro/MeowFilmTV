package tv.meowfilm.app.ui

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class VideoSourcePayload(
    val siteKey: String,
    val siteName: String,
    val spiderApi: String,
    val videoId: String,
)

data class VideoPayload(
    val title: String,
    val posterUrl: String = "",
    val remark: String = "",
    val sources: List<VideoSourcePayload> = emptyList(),
)

object NavPayload {
    fun encode(video: VideoPayload): String {
        val obj =
            JSONObject()
                .put("title", video.title)
                .put("posterUrl", video.posterUrl)
                .put("remark", video.remark)
        val arr = JSONArray()
        video.sources.forEach { s ->
            arr.put(
                JSONObject()
                    .put("siteKey", s.siteKey)
                    .put("siteName", s.siteName)
                    .put("spiderApi", s.spiderApi)
                    .put("videoId", s.videoId),
            )
        }
        obj.put("sources", arr)
        val bytes = obj.toString().toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun decode(payload: String): VideoPayload? {
        val raw = payload.trim()
        if (raw.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(raw, Base64.URL_SAFE)
            val obj = JSONObject(String(bytes, Charsets.UTF_8))
            val title = obj.optString("title").orEmpty()
            val posterUrl = obj.optString("posterUrl").orEmpty()
            val remark = obj.optString("remark").orEmpty()
            val sourcesArr = obj.optJSONArray("sources") ?: JSONArray()
            val sources = ArrayList<VideoSourcePayload>(sourcesArr.length())
            for (i in 0 until sourcesArr.length()) {
                val s = sourcesArr.optJSONObject(i) ?: continue
                val siteKey = s.optString("siteKey").orEmpty()
                val spiderApi = s.optString("spiderApi").orEmpty()
                val videoId = s.optString("videoId").orEmpty()
                if (siteKey.isBlank() || spiderApi.isBlank() || videoId.isBlank()) continue
                sources +=
                    VideoSourcePayload(
                        siteKey = siteKey,
                        siteName = s.optString("siteName").orEmpty().ifBlank { siteKey },
                        spiderApi = spiderApi,
                        videoId = videoId,
                    )
            }
            if (title.isBlank()) null else VideoPayload(title = title, posterUrl = posterUrl, remark = remark, sources = sources)
        }.getOrNull()
    }
}

