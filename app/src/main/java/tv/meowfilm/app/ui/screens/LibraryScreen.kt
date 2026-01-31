package tv.meowfilm.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.meowfilm.app.ui.components.MeowFilmBackground
import tv.meowfilm.app.ui.components.MediaCard
import tv.meowfilm.app.ui.LocalWatchHistoryRepository
import tv.meowfilm.app.ui.NavPayload
import tv.meowfilm.app.ui.VideoPayload
import tv.meowfilm.app.ui.VideoSourcePayload

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenHistory: () -> Unit,
) {
    LibraryScreen(
        title = "收藏",
        items = remember { emptyList() },
        onBack = onBack,
        onOpenDetail = onOpenDetail,
        selectedTab = 0,
        onSelectTab = { if (it == 1) onOpenHistory() },
    )
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val historyRepo = LocalWatchHistoryRepository.current
    LibraryScreen(
        title = "历史",
        items =
            remember(historyRepo.items) {
                historyRepo.items.map { h ->
                    val badge =
                        if (h.episodeIndex > 0) "上次看到${h.episodeIndex.toString().padStart(2, '0')}"
                        else ""
                    val sources =
                        if (h.siteKey.isNotBlank() && h.spiderApi.isNotBlank() && h.videoId.isNotBlank()) {
                            listOf(
                                VideoSourcePayload(
                                    siteKey = h.siteKey,
                                    siteName = h.siteName.ifBlank { h.siteKey },
                                    spiderApi = h.spiderApi,
                                    videoId = h.videoId,
                                ),
                            )
                        } else {
                            emptyList()
                        }
                    val payload =
                        NavPayload.encode(
                            VideoPayload(
                                title = h.title,
                                posterUrl = h.posterUrl,
                                remark = "",
                                sources = sources,
                            ),
                        )
                    UiMedia(
                        title = h.title,
                        subtitle = badge,
                        accent = Color(0xFFA7F3D0),
                        id = payload,
                        posterUrl = h.posterUrl,
                        rating = "",
                    )
                }
            },
        onBack = onBack,
        onOpenDetail = onOpenDetail,
        selectedTab = 1,
        onSelectTab = { if (it == 0) onOpenFavorites() },
    )
}

@Composable
private fun LibraryScreen(
    title: String,
    items: List<UiMedia>,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        MeowFilmBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, start = 26.dp, end = 26.dp, bottom = 26.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(
                    icon = Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LibraryTabs(
                selectedIndex = selectedTab,
                onSelect = onSelectTab,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0x11000000),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(items) { it ->
                        MediaCard(
                            title = it.title,
                            subtitle = it.subtitle,
                            accent = it.accent,
                            onClick = { onOpenDetail(it.id.ifBlank { it.title }) },
                            modifier = Modifier.width(214.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LibraryTabPill(
            text = "收藏",
            selected = selectedIndex == 0,
            onClick = { onSelect(0) },
        )
        LibraryTabPill(
            text = "历史",
            selected = selectedIndex == 1,
            onClick = { onSelect(1) },
        )
    }
}

@Composable
private fun LibraryTabPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.05f else 1.0f, label = "libPillScale")
    val border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

    Surface(
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = if (focused) 8.dp else 0.dp,
        shadowElevation = if (focused) 12.dp else 0.dp,
        border = border,
        color = if (selected) MaterialTheme.colorScheme.secondary else Color(0xFF1A1F27),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color =
                if (selected) MaterialTheme.colorScheme.onSecondary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.08f else 1.0f, label = "navIconScale")

    Surface(
        modifier = modifier
            .size(44.dp)
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
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = if (focused) Color(0x33000000) else Color.Transparent,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
