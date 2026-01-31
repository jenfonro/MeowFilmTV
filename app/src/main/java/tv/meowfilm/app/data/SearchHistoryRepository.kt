package tv.meowfilm.app.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

@Stable
class SearchHistoryRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var items by mutableStateOf(load())
        private set

    fun add(query: String) {
        val q = query.trim()
        if (q.isBlank()) return

        val prev = items
        val next = ArrayList<String>(minOf(prev.size + 1, MAX_ITEMS))
        next.add(q)
        prev.forEach { it ->
            if (next.size >= MAX_ITEMS) return@forEach
            if (it.equals(q, ignoreCase = true)) return@forEach
            next.add(it)
        }
        items = next
        persist(next)
    }

    fun remove(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val next = items.filterNot { it.equals(q, ignoreCase = true) }
        items = next
        persist(next)
    }

    fun clear() {
        items = emptyList()
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    private fun load(): List<String> {
        val raw = prefs.getString(KEY_ITEMS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val v = arr.optString(i).trim()
                if (v.isBlank()) continue
                out.add(v)
            }
            out
        }.getOrElse { emptyList() }
    }

    private fun persist(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private companion object {
        private const val PREFS_NAME = "meowfilm_search_history"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 24
    }
}

