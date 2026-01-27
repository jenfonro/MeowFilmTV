package tv.meowfilm.app.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class WatchHistoryItem(
    val contentKey: String,
    val title: String,
    val posterUrl: String,
    val siteKey: String,
    val siteName: String,
    val spiderApi: String,
    val videoId: String,
    val playFlag: String,
    val episodeIndex: Int,
    val episodeName: String,
    val updatedAt: Long,
    val pendingSync: Boolean,
)

@Stable
class WatchHistoryRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var items by mutableStateOf(load())
        private set

    fun record(
        title: String,
        posterUrl: String = "",
        episodeIndex: Int = 1,
    ) {
        recordFull(
            title = title,
            posterUrl = posterUrl,
            siteKey = "",
            siteName = "",
            spiderApi = "",
            videoId = "",
            playFlag = "",
            episodeIndex = episodeIndex,
            episodeName = "",
            updatedAtMs = System.currentTimeMillis(),
            pendingSync = true,
        )
    }

    fun recordFull(
        title: String,
        posterUrl: String,
        siteKey: String,
        siteName: String,
        spiderApi: String,
        videoId: String,
        playFlag: String,
        episodeIndex: Int,
        episodeName: String,
        updatedAtMs: Long = System.currentTimeMillis(),
        pendingSync: Boolean = true,
    ) {
        val t = title.trim()
        if (t.isBlank()) return
        val now = updatedAtMs.coerceAtLeast(0L)
        val key = normalizeContentKey(t)

        val prev = items
        val existing = prev.firstOrNull { it.contentKey == key || it.title == t }
        val next =
            WatchHistoryItem(
                contentKey = key,
                title = t,
                posterUrl = posterUrl.trim().ifBlank { existing?.posterUrl.orEmpty() },
                siteKey = siteKey.trim().ifBlank { existing?.siteKey.orEmpty() },
                siteName = siteName.trim().ifBlank { existing?.siteName.orEmpty() },
                spiderApi = spiderApi.trim().ifBlank { existing?.spiderApi.orEmpty() },
                videoId = videoId.trim().ifBlank { existing?.videoId.orEmpty() },
                playFlag = playFlag.trim().ifBlank { existing?.playFlag.orEmpty() },
                episodeIndex = episodeIndex.coerceAtLeast(0),
                episodeName = episodeName.trim().ifBlank { existing?.episodeName.orEmpty() },
                updatedAt = now,
                pendingSync = pendingSync || (existing?.pendingSync == true),
            )
        val merged = ArrayList<WatchHistoryItem>(minOf(prev.size + 1, MAX_ITEMS))
        merged.add(next)
        prev.forEach { it ->
            if (it.contentKey == next.contentKey || it.title == t) return@forEach
            if (merged.size >= MAX_ITEMS) return@forEach
            merged.add(it)
        }
        items = merged
        persist(merged)
    }

    fun mergeFromServer(list: List<ServerPlayHistoryItem>) {
        if (list.isEmpty()) return
        val prev = items
        val byKey = LinkedHashMap<String, WatchHistoryItem>()
        prev.forEach { byKey[it.contentKey.ifBlank { normalizeContentKey(it.title) }] = it }
        list.forEach { s ->
            val key = s.contentKey.ifBlank { normalizeContentKey(s.videoTitle) }
            val serverMs = s.updatedAtSec * 1000L
            val local = byKey[key]
            if (local == null || serverMs >= local.updatedAt) {
                byKey[key] =
                    WatchHistoryItem(
                        contentKey = key,
                        title = s.videoTitle,
                        posterUrl = s.videoPoster,
                        siteKey = s.siteKey,
                        siteName = s.siteName,
                        spiderApi = s.spiderApi,
                        videoId = s.videoId,
                        playFlag = s.playFlag,
                        episodeIndex = s.episodeIndex,
                        episodeName = s.episodeName,
                        updatedAt = serverMs,
                        pendingSync = false,
                    )
            }
        }
        val merged = byKey.values.sortedByDescending { it.updatedAt }.take(MAX_ITEMS)
        items = merged
        persist(merged)
    }

    fun markSynced(contentKey: String) {
        val key = contentKey.trim()
        if (key.isBlank()) return
        val prev = items
        val next = prev.map {
            if (it.contentKey == key) it.copy(pendingSync = false) else it
        }
        items = next
        persist(next)
    }

    fun clear() {
        items = emptyList()
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    fun saveLastServerSyncAt(epochMs: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_MS, epochMs.coerceAtLeast(0L)).apply()
    }

    fun lastServerSyncAt(): Long = prefs.getLong(KEY_LAST_SYNC_MS, 0L).coerceAtLeast(0L)

    private fun load(): List<WatchHistoryItem> {
        val raw = prefs.getString(KEY_ITEMS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<WatchHistoryItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("title", "").trim()
                if (title.isBlank()) continue
                val contentKey = o.optString("contentKey", "").trim().ifBlank { normalizeContentKey(title) }
                out.add(
                    WatchHistoryItem(
                        contentKey = contentKey,
                        title = title,
                        posterUrl = o.optString("posterUrl", "").trim(),
                        siteKey = o.optString("siteKey", "").trim(),
                        siteName = o.optString("siteName", "").trim(),
                        spiderApi = o.optString("spiderApi", "").trim(),
                        videoId = o.optString("videoId", "").trim(),
                        playFlag = o.optString("playFlag", "").trim(),
                        episodeIndex = o.optInt("episodeIndex", 0).coerceAtLeast(0),
                        episodeName = o.optString("episodeName", "").trim(),
                        updatedAt = o.optLong("updatedAt", 0L),
                        pendingSync = o.optBoolean("pendingSync", false),
                    ),
                )
            }
            out
        }.getOrElse { emptyList() }
    }

    private fun persist(list: List<WatchHistoryItem>) {
        val arr = JSONArray()
        list.forEach { it ->
            val o = JSONObject()
            o.put("contentKey", it.contentKey)
            o.put("title", it.title)
            o.put("posterUrl", it.posterUrl)
            o.put("siteKey", it.siteKey)
            o.put("siteName", it.siteName)
            o.put("spiderApi", it.spiderApi)
            o.put("videoId", it.videoId)
            o.put("playFlag", it.playFlag)
            o.put("episodeIndex", it.episodeIndex)
            o.put("episodeName", it.episodeName)
            o.put("updatedAt", it.updatedAt)
            o.put("pendingSync", it.pendingSync)
            arr.put(o)
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private fun normalizeContentKey(s: String): String {
        val raw = s.trim()
        if (raw.isBlank()) return ""
        return raw
            .lowercase()
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("\\s+"), "")
            .replace(Regex("[·•・.。:：,，/\\\\\\-—_()（）\\[\\]{}<>《》“”\"'‘’!?！？|]+"), "")
            .trim()
    }

    private companion object {
        private const val PREFS_NAME = "meowfilm_history"
        private const val KEY_ITEMS = "items"
        private const val KEY_LAST_SYNC_MS = "last_server_sync_ms"
        private const val MAX_ITEMS = 20
    }
}
