package tv.meowfilm.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

@Composable
fun TopTabsBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    username: String = "",
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusInTopBar: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { idx, label ->
                TopTab(
                    label = label,
                    selected = idx == selectedIndex,
                    onClick = { onSelect(idx) },
                    onFocused = onFocusInTopBar,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        val u = username.trim()
        if (u.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .height(36.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color(0x33000000),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = u,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                        maxLines = 1,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        IconAction(
            icon = Icons.Outlined.FavoriteBorder,
            contentDescription = "Favorites",
            onClick = onOpenFavorites,
            onFocused = onFocusInTopBar,
        )

        Spacer(modifier = Modifier.width(10.dp))

        IconAction(
            icon = Icons.Outlined.History,
            contentDescription = "History",
            onClick = onOpenHistory,
            onFocused = onFocusInTopBar,
        )

        Spacer(modifier = Modifier.width(10.dp))

        IconAction(
            icon = Icons.Outlined.Settings,
            contentDescription = "Settings",
            onClick = onOpenSettings,
            onFocused = onFocusInTopBar,
        )
    }
}

@Composable
private fun TopTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1.0f, label = "tabScale")
    val textScale by animateFloatAsState(if (selected) 1.0f else 0.90f, label = "tabTextScale")

    Box(
        modifier = Modifier
            .widthIn(min = 74.dp)
            .height(40.dp)
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick)
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionCenter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = label,
                modifier = Modifier.scale(textScale),
                style = if (selected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color =
                    when {
                        selected -> MaterialTheme.colorScheme.onSurface
                        focused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                    },
            )
            if (selected) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(
                    modifier =
                        Modifier
                            .width(30.dp)
                            .height(3.dp),
                    thickness = 3.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1.0f, label = "iconScale")

    Surface(
        modifier = modifier
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
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
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = if (focused) Color(0x33000000) else Color.Transparent,
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint =
                    if (focused) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            )
        }
    }
}
