package tv.meowfilm.app.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.meowfilm.app.data.AppSettings
import tv.meowfilm.app.data.DoubanClient
import tv.meowfilm.app.ui.screens.UiMedia
import kotlin.math.abs
import androidx.compose.ui.graphics.Color

@Stable
class DoubanHomeStore {
    private val cached = mutableStateMapOf<String, List<UiMedia>>()
    private val loading = mutableStateMapOf<String, Boolean>()
    private val errors = mutableStateMapOf<String, String>()

    private var lastSettingsKey by mutableStateOf<String?>(null)

    fun items(type: String): List<UiMedia> = cached[type] ?: emptyList()

    fun isLoading(type: String): Boolean = loading[type] == true

    fun error(type: String): String = errors[type].orEmpty()

    fun ensurePrefetch(
        scope: CoroutineScope,
        settings: AppSettings,
        types: List<String> = listOf("movie", "tv", "anime", "show"),
    ) {
        val key = settingsKey(settings)
        if (lastSettingsKey != key) {
            lastSettingsKey = key
            cached.clear()
            loading.clear()
            errors.clear()
        }
        types.forEach { ensureLoaded(scope, settings, it) }
    }

    fun ensureLoaded(
        scope: CoroutineScope,
        settings: AppSettings,
        type: String,
        limit: Int = 40,
    ) {
        val t = type.trim()
        if (t.isBlank()) return
        if (loading[t] == true) return
        if (cached.containsKey(t)) return

        loading[t] = true
        errors.remove(t)
        scope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching { DoubanClient.fetchHomeRow(t, settings, limit = limit) }
                }
            if (result.isSuccess) {
                cached[t] =
                    result.getOrDefault(emptyList()).map { it ->
                        val rate = it.rate.trim()
                        UiMedia(
                            title = it.title,
                            subtitle = "",
                            accent = accentFromString(it.title),
                            id = it.id,
                            posterUrl = it.poster,
                            rating = rate,
                        )
                    }
            } else {
                cached[t] = emptyList()
                errors[t] = result.exceptionOrNull()?.message ?: "error"
            }
            loading[t] = false
        }
    }

    private fun settingsKey(s: AppSettings): String =
        listOf(
            s.doubanDataProxy.trim(),
            s.doubanDataCustom.trim(),
            s.doubanImgProxy.trim(),
            s.doubanImgCustom.trim(),
            s.serverUrl.trim(),
        ).joinToString("|")

    private fun accentFromString(seed: String): Color {
        val palette =
            listOf(
                Color(0xFF7DD3FC),
                Color(0xFFA7F3D0),
                Color(0xFFC7D2FE),
                Color(0xFFFDA4AF),
                Color(0xFFFDE68A),
            )
        val idx = abs(seed.hashCode()) % palette.size
        return palette[idx]
    }
}
