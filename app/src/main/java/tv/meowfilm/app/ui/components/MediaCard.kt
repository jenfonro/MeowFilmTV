package tv.meowfilm.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    posterUrl: String = "",
    rating: String = "",
    topLeftBadge: String = "",
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1.0f, label = "cardScale")
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable(),
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = if (focused) 22.dp else 0.dp,
        color = Color.Transparent,
    ) {
        val poster = rememberRemoteImage(posterUrl)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(214f / 280f)
                .clip(shape)
                .background(Color.Transparent, shape),
        ) {
            if (poster != null) {
                Image(
                    bitmap = poster,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0x22000000),
                                        Color(0x11000000),
                                    ),
                                ),
                                shape = shape,
                            ),
                )
            }

            if (focused) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.06f)),
                )
            }

            val r = rating.trim()
            if (r.isNotBlank()) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(34.dp),
                    shape = CircleShape,
                    color = Color(0xAA0B0F14),
                    border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = r,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            color = Color(0xFFFF5FA0),
                        )
                    }
                }
            }

            val badge = subtitle.trim()
            if (badge.isNotBlank()) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 10.dp, bottom = 54.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.92f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        text = badge,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.96f),
                    )
                }
            }

            val corner = topLeftBadge.trim()
            if (corner.isNotBlank()) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 10.dp, top = 10.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xAA0B0F14),
                    border = BorderStroke(1.dp, Color(0x44FFFFFF)),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        text = corner,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.96f),
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(Color(0x99000000))
                        .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.96f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
