package tv.meowfilm.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.random.Random

@Composable
fun GlassLayer(
    modifier: Modifier = Modifier,
    tint: Color = Color(0x66000000),
    content: @Composable BoxScope.() -> Unit,
) {
    val noise = remember { generateNoiseBitmap(96, 96, seed = 7) }
    val gradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0x22FFFFFF),
                Color(0x11000000),
                Color(0x00FFFFFF),
            ),
        )
    }

    Box(
        modifier = modifier
            .background(tint)
            .background(gradient)
            .drawWithCache {
                val bmp = noise
                onDrawWithContent {
                    drawContent()
                    drawNoiseTiled(bmp, alpha = 0.065f)
                }
            },
        content = content,
    )
}

private fun generateNoiseBitmap(w: Int, h: Int, seed: Int): Bitmap {
    val rnd = Random(seed)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val v = rnd.nextInt(0, 255)
            val a = 0x22
            val c = (a shl 24) or (v shl 16) or (v shl 8) or v
            bmp.setPixel(x, y, c)
        }
    }
    return bmp
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNoiseTiled(
    noise: Bitmap,
    alpha: Float,
) {
    val tileW = noise.width.toFloat()
    val tileH = noise.height.toFloat()
    if (tileW <= 0f || tileH <= 0f) return

    val cols = (size.width / tileW).toInt() + 2
    val rows = (size.height / tileH).toInt() + 2

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = false
            this.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        }
        val native = canvas.nativeCanvas
        for (r in 0 until rows) {
            val y = (r * tileH).toInt()
            for (c in 0 until cols) {
                val x = (c * tileW).toInt()
                native.drawBitmap(noise, x.toFloat(), y.toFloat(), paint)
            }
        }
    }
}
