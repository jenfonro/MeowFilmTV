package tv.meowfilm.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import tv.meowfilm.app.ui.LocalAppSettingsRepository

@Composable
fun MeowFilmBackground() {
    val repo = LocalAppSettingsRepository.current
    val wallpaperPath = repo.settings.wallpaperLocalPath.trim()
    val bitmapState = remember(wallpaperPath) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(wallpaperPath) {
        if (wallpaperPath.isBlank()) {
            bitmapState.value = null
            return@LaunchedEffect
        }
        bitmapState.value =
            withContext(Dispatchers.IO) {
                try {
                    val f = File(wallpaperPath)
                    if (!f.exists() || f.length() <= 0L) return@withContext null
                    val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return@withContext null
                    bmp.asImageBitmap()
                } catch (_e: Throwable) {
                    null
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bmp = bitmapState.value
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (isLight) Color(0xFFF6F8FC) else Color(0xFF0B0F14),
                                if (isLight) Color(0xFFEFF4FF) else Color(0xFF071018),
                                if (isLight) Color(0xFFFDFEFF) else Color(0xFF05070A),
                            ),
                        ),
                    ),
            )
        }

        val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isLight) Color(0x3D46B3FF) else Color(0x3346B3FF),
                            if (isLight) Color(0x00FFFFFF) else Color(0x001B1F26),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x00000000),
                            if (isLight) Color(0x1A000000) else Color(0x66000000),
                            if (isLight) Color(0x33000000) else Color(0xCC000000),
                        ),
                    ),
                ),
        )

        GlassLayer(
            modifier = Modifier.fillMaxSize(),
            tint = Color.Transparent,
        ) {}
    }
}
