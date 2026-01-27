package tv.meowfilm.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tv.meowfilm.app.ui.LocalAppSettingsRepository
import tv.meowfilm.app.ui.NavPayload
import tv.meowfilm.app.ui.VideoPayload
import tv.meowfilm.app.ui.VideoSourcePayload
import tv.meowfilm.app.ui.components.MeowFilmBackground
import tv.meowfilm.app.ui.components.MediaCard
import tv.meowfilm.app.ui.LocalAppScope
import tv.meowfilm.app.ui.LocalMeowFilmSessionRepository
import tv.meowfilm.app.ui.meowfilm.LocalMeowFilmStore
import tv.meowfilm.app.ui.search.LocalSearchResultsStore
import tv.meowfilm.app.ui.search.SearchResultsStore

@Composable
fun SearchResultsScreen(
    query: String,
    onBack: () -> Unit,
    onOpenDetail: (payload: String) -> Unit,
) {
    BackHandler { onBack() }

    val settings = LocalAppSettingsRepository.current.settings
    val meowFilmStore = LocalMeowFilmStore.current
    val sessionRepo = LocalMeowFilmSessionRepository.current
    val q = query.trim()
    val appScope = LocalAppScope.current
    val store = LocalSearchResultsStore.current
    val state = store.stateFor(settings, q)

    LaunchedEffect(state.key, meowFilmStore.bootstrap) {
        store.ensureSearch(appScope, settings, meowFilmStore, sessionRepo, q)
    }

    fun openDetailFromItem(it: SearchResultsStore.RawItem) {
        if (it.siteKey == "AGG") {
            val sources = state.exactSources.values.distinctBy { "${it.siteKey}::${it.videoId}" }
            val payload =
                NavPayload.encode(
                    VideoPayload(
                        title = it.title,
                        posterUrl = it.poster,
                        remark = it.remark,
                        sources = sources,
                    ),
                )
            onOpenDetail(payload)
            return
        }
        val payload =
            NavPayload.encode(
                VideoPayload(
                    title = it.title,
                    posterUrl = it.poster,
                    remark = it.remark,
                    sources =
                        listOf(
                            VideoSourcePayload(
                                siteKey = it.siteKey,
                                siteName = it.siteName,
                                spiderApi = it.spiderApi,
                                videoId = it.videoId,
                            ),
                        ),
                ),
            )
        onOpenDetail(payload)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MeowFilmBackground()

        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Left filters
            Column(modifier = Modifier.width(260.dp)) {
                Text(
                    text = "“$q”的搜索结果",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(14.dp))

                val allCount = state.items.size
                val filterKeys =
                    state.siteCounts.entries
                        .filter { it.value > 0 }
                        .sortedByDescending { it.value }
                        .map { it.key }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    item {
                        FilterPill(
                            text = if (allCount > 0) "全部（$allCount）" else "全部",
                            selected = state.selectedFilterKey == "ALL",
                            onClick = { state.selectedFilterKey = "ALL" },
                        )
                    }
                    items(filterKeys) { key ->
                        val name = state.siteNames[key] ?: key
                        val cnt = state.siteCounts[key] ?: 0
                        FilterPill(
                            text = if (cnt > 0) "$name（$cnt）" else name,
                            selected = state.selectedFilterKey == key,
                            onClick = { state.selectedFilterKey = key },
                        )
                    }
                }
            }

            // Right grid (streaming)
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.loading) {
                        Text(
                            text = "搜索中… ${state.done.intValue}/${state.total.intValue}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                        )
                    } else if (state.done.intValue > 0) {
                        Text(
                            text = "已完成 ${state.done.intValue}/${state.total.intValue}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                val agg = store.buildAggregateCard(state)
                val list =
                    if (state.selectedFilterKey == "ALL") {
                        val base = state.items.toList()
                        if (agg != null) listOf(agg) + base else base
                    } else {
                        state.items.filter { it.siteKey == state.selectedFilterKey }
                    }

                when {
                    state.error.isNotBlank() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = state.error, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f))
                        }
                    }

                    list.isEmpty() && state.loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "正在搜索…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f))
                        }
                    }

                    list.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "暂无结果", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f))
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(list) { it ->
                                val badge = if (it.siteKey == "AGG") it.siteName else it.siteName
                                MediaCard(
                                    title = it.title,
                                    subtitle = it.remark,
                                    accent = MaterialTheme.colorScheme.primary,
                                    onClick = { openDetailFromItem(it) },
                                    posterUrl = it.poster,
                                    rating = "",
                                    topLeftBadge = badge,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1.0f, label = "filterScale")
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable(),
        shape = shape,
        color =
            when {
                selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                focused -> Color(0x33000000)
                else -> Color(0x22000000)
            },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Text(
                modifier = Modifier.padding(horizontal = 18.dp),
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                maxLines = 1,
            )
        }
    }
}
