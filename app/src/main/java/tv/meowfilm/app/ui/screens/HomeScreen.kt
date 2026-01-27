package tv.meowfilm.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.meowfilm.app.ui.components.MediaCard
import tv.meowfilm.app.ui.LocalAppSettingsRepository
import tv.meowfilm.app.ui.LocalWatchHistoryRepository
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.runtime.rememberCoroutineScope
import tv.meowfilm.app.ui.home.LocalDoubanHomeStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.meowfilm.app.ui.NavPayload
import tv.meowfilm.app.ui.VideoPayload

data class UiMedia(
    val title: String,
    val subtitle: String,
    val accent: Color,
    val id: String = "",
    val posterUrl: String = "",
    val rating: String = "",
)

@Composable
fun HomeTabContent(
    selectedCategoryIndex: Int,
    onSelectCategory: (Int) -> Unit,
    onOpenDetail: (payload: String) -> Unit,
    onOpenSearchResults: (title: String) -> Unit,
    onTopAreaFocus: () -> Unit,
    onContentFocus: () -> Unit,
    onCategoryFocusChanged: (Boolean) -> Unit,
    categoryFocusRequesters: List<FocusRequester>,
    modifier: Modifier = Modifier,
) {
    val repo = LocalAppSettingsRepository.current
    val settings = repo.settings
    val store = LocalDoubanHomeStore.current
    val historyRepo = LocalWatchHistoryRepository.current
    val historyItems = historyRepo.items
    val scope = rememberCoroutineScope()

    val siteCategories = remember { listOf("电影", "剧集", "动漫", "综艺") }
    val types = remember { listOf("movie", "tv", "anime", "show") }
    val currentType = types.getOrNull(selectedCategoryIndex) ?: "movie"

    val started = remember { mutableIntStateOf(0) } // 0=false 1=true
    LaunchedEffect(
        currentType,
        settings.doubanDataProxy,
        settings.doubanDataCustom,
        settings.doubanImgProxy,
        settings.doubanImgCustom,
        settings.serverUrl,
    ) {
        started.intValue = 1
        store.ensurePrefetch(scope = scope, settings = settings)
    }

    val remoteItems = store.items(currentType)
    val isLoading = store.isLoading(currentType)
    val gridState = rememberLazyGridState()
    val columns = 5
    val headerCount = (if (historyItems.isNotEmpty()) 2 else 0) + 1

    fun centerGridItem(index: Int, cardRow: Int) {
        if (cardRow <= 0) return
        scope.launch {
            val visible = gridState.layoutInfo.visibleItemsInfo.any { it.index == index }
            if (!visible) {
                gridState.animateScrollToItem(index)
            }
            delay(16)
            val layout = gridState.layoutInfo
            val info = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return@launch
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            val itemCenter = info.offset.y + info.size.height / 2
            val delta = (itemCenter - viewportCenter).toFloat()
            gridState.animateScrollBy(delta)
        }
    }

    Column(modifier = modifier) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(siteCategories.size) { idx ->
                CategoryPill(
                    label = siteCategories[idx],
                    selected = idx == selectedCategoryIndex,
                    onClick = { onSelectCategory(idx) },
                    onFocused = {
                        onTopAreaFocus()
                        onCategoryFocusChanged(true)
                    },
                    focusRequester = categoryFocusRequesters.getOrNull(idx),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (historyItems.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        text = "最近观看",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(historyItems.take(10)) { h ->
                            val badge =
                                if (h.episodeIndex > 0) "上次看到${h.episodeIndex.toString().padStart(2, '0')}"
                                else ""
                            MediaCard(
                                title = h.title,
                                subtitle = badge,
                                accent = Color(0xFFA7F3D0),
                                onClick = { onOpenDetail(NavPayload.encode(VideoPayload(title = h.title))) },
                                posterUrl = h.posterUrl,
                                rating = "",
                                modifier = Modifier.width(214.dp),
                            )
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                val categoryLabel = siteCategories.getOrNull(selectedCategoryIndex) ?: ""
                Text(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    text = if (categoryLabel.isBlank()) "豆瓣精选" else "豆瓣精选 · $categoryLabel",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (remoteItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val loading = isLoading || started.intValue == 0
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loading) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp,
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.22f),
                                )
                                Text(
                                    text = "正在加载…",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.92f),
                                )
                            }
                        } else {
                            Text(
                                text = "暂无内容",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.92f),
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(remoteItems) { idx, it ->
                    val cardRow = idx / columns
                    val gridIndex = headerCount + idx
                    MediaCard(
                        title = it.title,
                        subtitle = it.subtitle,
                        accent = it.accent,
                        onClick = {
                            onOpenSearchResults(it.title)
                        },
                        posterUrl = it.posterUrl,
                        rating = it.rating,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(214f / 280f)
                                .onFocusChanged { f ->
                                    if (f.isFocused) {
                                        onCategoryFocusChanged(false)
                                        onContentFocus()
                                        centerGridItem(gridIndex, cardRow)
                                    }
                                },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val focused = remember { mutableIntStateOf(0) }
    val isFocused = focused.intValue == 1
    val scale = animateFloatAsState(if (isFocused) 1.03f else 1.0f, label = "pillScale").value
    val shape = RoundedCornerShape(999.dp)

    val bg =
        when {
            selected -> Color(0x440B0F14)
            else -> Color(0x2B0B0F14)
        }
    val fg =
        when {
            selected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f)
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
        }
    val border =
        when {
            isFocused -> BorderStroke(2.dp, Color.White.copy(alpha = 0.85f))
            selected -> BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
            else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        }

    Surface(
        modifier = modifier
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { f ->
                focused.intValue = if (f.isFocused) 1 else 0
                if (f.isFocused) onFocused()
            }
            .clickable(onClick = onClick)
            .focusable(),
        shape = shape,
        tonalElevation = if (isFocused) 8.dp else 2.dp,
        shadowElevation = if (isFocused) 12.dp else 0.dp,
        border = border,
        color = bg,
    ) {
        Text(
            modifier = Modifier
                .height(46.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = fg,
        )
    }
}
