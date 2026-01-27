package tv.meowfilm.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.meowfilm.app.data.HttpClient

private object MemoryImageCache {
    private val cache = LruCache<String, ImageBitmap>(80)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, value: ImageBitmap) {
        cache.put(key, value)
    }
}

@Composable
fun rememberRemoteImage(url: String): ImageBitmap? {
    val key = url.trim()
    val state = remember(key) { mutableStateOf(MemoryImageCache.get(key)) }

    LaunchedEffect(key) {
        if (key.isBlank()) {
            state.value = null
            return@LaunchedEffect
        }
        if (state.value != null) return@LaunchedEffect

        val bmp =
            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = HttpClient.getBytes(key)
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                    decoded.asImageBitmap()
                }.getOrNull()
            }
        if (bmp != null) {
            MemoryImageCache.put(key, bmp)
        }
        state.value = bmp
    }

    return state.value
}

