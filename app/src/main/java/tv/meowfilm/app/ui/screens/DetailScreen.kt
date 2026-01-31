package tv.meowfilm.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import tv.meowfilm.app.ui.components.MeowFilmBackground
import tv.meowfilm.app.ui.components.MeowPlayerController
import tv.meowfilm.app.ui.components.MeowPlayerView
import tv.meowfilm.app.ui.LocalWatchHistoryRepository
import tv.meowfilm.app.ui.VideoPayload
import tv.meowfilm.app.ui.meowfilm.LocalMeowFilmStore
import tv.meowfilm.app.data.CatPawOpenClient
import tv.meowfilm.app.data.MagicRules

private data class Episode(
    val rawName: String,
    val matchedLabel: String,
    val number: Int,
    val flag: String,
    val id: String,
)

private data class PlayLine(
    val flag: String,
    val episodes: List<Episode>,
)

@Composable
fun DetailScreen(
    payload: VideoPayload,
    onBack: () -> Unit,
) {
    var fullScreen by rememberSaveable(payload.title) { mutableStateOf(false) }
    BackHandler(enabled = fullScreen) { fullScreen = false }
    BackHandler(enabled = !fullScreen) { onBack() }

    val historyRepo = LocalWatchHistoryRepository.current
    val meowFilmStore = LocalMeowFilmStore.current
    val title = payload.title
    val accent = remember(title) { accentFromString(title) }
    val selectedSource = rememberSaveable(title) { mutableIntStateOf(0) } // 站点源（聚合来源）
    val selectedEpisode = rememberSaveable(title) { mutableIntStateOf(0) } // 第几集（0-based within filtered list）
    val selectedLine = rememberSaveable(title) { mutableIntStateOf(0) } // play_from index
    var showRawList by rememberSaveable(title) { mutableStateOf(false) }
    var descending by rememberSaveable(title) { mutableStateOf(false) }
    val selectedRange = rememberSaveable(title) { mutableIntStateOf(0) } // 0-based group of 20
    var favorite by rememberSaveable(title) { mutableStateOf(false) }
    var showSourcePicker by rememberSaveable(title) { mutableStateOf(false) }

    var detailLoading by rememberSaveable(title) { mutableStateOf(false) }
    var detailError by rememberSaveable(title) { mutableStateOf("") }
    var playLines by remember(title) { mutableStateOf<List<PlayLine>>(emptyList()) }

    var playUrl by rememberSaveable(title) { mutableStateOf("") }
    var playHeaders by remember(title) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var playError by rememberSaveable(title) { mutableStateOf("") }
    var detailActor by rememberSaveable(title) { mutableStateOf("") }
    var detailContent by rememberSaveable(title) { mutableStateOf("") }
    var showPlayInfo by rememberSaveable(title) { mutableStateOf(false) }

    val availableSources = payload.sources
    val currentSource = availableSources.getOrNull(selectedSource.intValue)
    val canSwitchSource = availableSources.size > 1

    val context = LocalContext.current
    val playerController = remember(title, currentSource?.videoId) { MeowPlayerController(context) }
    DisposableEffect(playerController) {
        onDispose { playerController.release() }
    }

    LaunchedEffect(playUrl, playHeaders) {
        playerController.setSource(playUrl, playHeaders)
    }

    // Default: 线路1 第1集 进入即播放（此处仅做状态准备，播放器后续接入）
    LaunchedEffect(title) {
        selectedSource.intValue = 0
        selectedEpisode.intValue = 0
        selectedLine.intValue = 0
        selectedRange.intValue = 0
        showRawList = false
        descending = false
    }

    LaunchedEffect(title, selectedSource.intValue, meowFilmStore.bootstrap) {
        val src = currentSource
        val apiBase = meowFilmStore.catApiBase()
        val tvUser = meowFilmStore.tvUser()
        if (src == null || apiBase.isBlank() || tvUser.isBlank()) {
            playLines = emptyList()
            detailLoading = false
            detailError = if (meowFilmStore.error.isNotBlank()) meowFilmStore.error else "暂无播放源"
            return@LaunchedEffect
        }

        detailLoading = true
        detailError = ""
        playError = ""
        playUrl = ""
        playHeaders = emptyMap()
        val boot = meowFilmStore.bootstrap
        val cleanRules = MagicRules.compileCleanRegexRules(boot?.settings?.magicEpisodeCleanRegexRules ?: emptyList())
        val episodeRules = MagicRules.compileReplaceRules(boot?.settings?.magicEpisodeRules ?: emptyList())

        val result =
            withContext(Dispatchers.IO) {
                runCatching {
                    CatPawOpenClient.detail(apiBase = apiBase, tvUser = tvUser, spiderApi = src.spiderApi, videoId = src.videoId)
                }
            }
        if (result.isFailure) {
            detailLoading = false
            detailError = result.exceptionOrNull()?.message ?: "请求失败"
            playLines = emptyList()
            return@LaunchedEffect
        }

        val d = result.getOrThrow()
        detailActor = d.actor
        detailContent = d.content
        val lines = parsePlayLines(d.playFrom, d.playUrl, cleanRules, episodeRules)
        playLines = lines
        detailLoading = false
        detailError = ""
        selectedLine.intValue = 0
        selectedEpisode.intValue = 0
        selectedRange.intValue = 0
        showRawList = false
    }

    LaunchedEffect(
        title,
        selectedSource.intValue,
        selectedEpisode.intValue,
        playLines,
        meowFilmStore.bootstrap,
    ) {
        val src = currentSource ?: return@LaunchedEffect
        val apiBase = meowFilmStore.catApiBase()
        val tvUser = meowFilmStore.tvUser()
        if (apiBase.isBlank() || tvUser.isBlank()) return@LaunchedEffect

        val line = playLines.getOrNull(selectedLine.intValue) ?: playLines.firstOrNull() ?: return@LaunchedEffect

        val list =
            buildEpisodeList(
                line = line,
                showRaw = showRawList,
                descending = descending,
            )
        val filtered = sliceEpisodeRange(list, selectedRange.intValue, size = 20)
        val ep = filtered.getOrNull(selectedEpisode.intValue) ?: return@LaunchedEffect

        val result =
            withContext(Dispatchers.IO) {
                runCatching {
                    CatPawOpenClient.play(
                        apiBase = apiBase,
                        tvUser = tvUser,
                        spiderApi = src.spiderApi,
                        flag = ep.flag,
                        id = ep.id,
                    )
                }
            }
        if (result.isSuccess) {
            val p = result.getOrThrow()
            playUrl = p.url
            playHeaders = p.headers
            playError = ""

            val playedLine = playLines.getOrNull(selectedLine.intValue) ?: playLines.firstOrNull()
            val playedList =
                if (playedLine != null) buildEpisodeList(playedLine, showRaw = showRawList, descending = descending) else emptyList()
            val playedFiltered = sliceEpisodeRange(playedList, selectedRange.intValue, size = 20)
            val playedEp = playedFiltered.getOrNull(selectedEpisode.intValue)
            val src = currentSource
            if (src != null) {
                historyRepo.recordFull(
                    title = title,
                    posterUrl = payload.posterUrl,
                    siteKey = src.siteKey,
                    siteName = src.siteName,
                    spiderApi = src.spiderApi,
                    videoId = src.videoId,
                    playFlag = playedEp?.flag.orEmpty(),
                    episodeIndex = playedEp?.number?.coerceAtLeast(1) ?: (selectedEpisode.intValue + 1).coerceAtLeast(1),
                    episodeName = (playedEp?.matchedLabel ?: playedEp?.rawName).orEmpty(),
                    pendingSync = true,
                )
            } else {
                historyRepo.record(
                    title = title,
                    posterUrl = payload.posterUrl,
                    episodeIndex = playedEp?.number?.coerceAtLeast(1) ?: (selectedEpisode.intValue + 1).coerceAtLeast(1),
                )
            }
        } else {
            playUrl = ""
            playHeaders = emptyMap()
            playError = result.exceptionOrNull()?.message ?: "请求失败"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MeowFilmBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, start = 26.dp, end = 26.dp, bottom = 26.dp),
        ) {
            TopLayout(
                title = title,
                accent = accent,
                selectedSourceLabel = currentSource?.siteName ?: "换源",
                actor = detailActor,
                content = detailContent,
                showPlayInfo = showPlayInfo,
                onTogglePlayInfo = { showPlayInfo = !showPlayInfo },
                playInfoLineLabel = (playLines.getOrNull(selectedLine.intValue)?.flag).orEmpty(),
                playInfoUrl = playUrl,
                isFavorite = favorite,
                onToggleFavorite = { favorite = !favorite },
                onPickSource = { if (canSwitchSource) showSourcePicker = true },
                playUrl = playUrl,
                playerController = playerController,
                onEnterFullScreen = { fullScreen = true },
                showSwitchSource = canSwitchSource,
            )

            Spacer(modifier = Modifier.height(14.dp))

            val eps = playLines.firstOrNull()?.episodes ?: emptyList()
            when {
                detailLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "正在加载…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                    }
                }

                detailError.isNotBlank() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = detailError, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                    }
                }

                eps.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "暂无选集", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                    }
                }

                else -> {
                    val lines = playLines
                    val currentLine = lines.getOrNull(selectedLine.intValue) ?: lines.firstOrNull()
                    val canPickLine = lines.size > 1
                    val lineEpisodes = currentLine?.episodes ?: emptyList()
                    val showRawToggle = lineEpisodes.size > 1
                    if (!showRawToggle) showRawList = true

                    val list =
                        if (currentLine != null) buildEpisodeList(currentLine, showRaw = showRawList, descending = descending) else emptyList()
                    val groups = ((list.size + 19) / 20).coerceAtLeast(1)
                    if (selectedRange.intValue >= groups) selectedRange.intValue = 0
                    val filtered = sliceEpisodeRange(list, selectedRange.intValue, size = 20)
                    if (selectedEpisode.intValue >= filtered.size) selectedEpisode.intValue = 0

                    EpisodeToolbar(
                        lineFlags = lines.map { it.flag },
                        selectedLine = selectedLine.intValue,
                        onSelectLine = { idx ->
                            selectedLine.intValue = idx
                            selectedEpisode.intValue = 0
                            selectedRange.intValue = 0
                            showRawList = false
                        },
                        canPickLine = canPickLine,
                        showRawToggle = showRawToggle,
                        showRaw = showRawList,
                        onToggleRaw = {
                            showRawList = !showRawList
                            selectedEpisode.intValue = 0
                            selectedRange.intValue = 0
                        },
                        descending = descending,
                        onToggleDescending = {
                            descending = !descending
                            selectedEpisode.intValue = 0
                            selectedRange.intValue = 0
                        },
                        ranges = (0 until groups).map { i ->
                            val start = i * 20 + 1
                            val end = minOf((i + 1) * 20, list.size)
                            "$start-$end"
                        },
                        selectedRange = selectedRange.intValue,
                        onSelectRange = {
                            selectedRange.intValue = it
                            selectedEpisode.intValue = 0
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    EpisodeGrid(
                        accent = accent,
                        episodes = filtered.map { it.displayLabel() },
                        selectedIndex = selectedEpisode.intValue,
                        onSelect = { selectedEpisode.intValue = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (showSourcePicker && canSwitchSource) {
            SourcePickerDialog(
                sources = availableSources.map { it.siteName.ifBlank { it.siteKey } },
                selectedIndex = selectedSource.intValue,
                onDismiss = { showSourcePicker = false },
                onSelect = { idx ->
                    selectedSource.intValue = idx
                    selectedEpisode.intValue = 0
                    showSourcePicker = false
                },
            )
        }

        if (fullScreen) {
            Dialog(onDismissRequest = { fullScreen = false }) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .onKeyEvent {
                                if (it.type == KeyEventType.KeyUp && it.key == Key.Back) {
                                    fullScreen = false
                                    true
                                } else {
                                    false
                                }
                            },
                ) {
                    MeowPlayerView(
                        controller = playerController,
                        useController = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopLayout(
    title: String,
    accent: Color,
    selectedSourceLabel: String,
    actor: String,
    content: String,
    showPlayInfo: Boolean,
    onTogglePlayInfo: () -> Unit,
    playInfoLineLabel: String,
    playInfoUrl: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPickSource: () -> Unit,
    playUrl: String,
    playerController: MeowPlayerController,
    onEnterFullScreen: () -> Unit,
    showSwitchSource: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // TVBox 对齐：详情页直接显示播放器区域（不放左侧海报卡片；选集时不重新创建播放器）
        PlayerBox(
            url = playUrl,
            controller = playerController,
            onEnterFullScreen = onEnterFullScreen,
            modifier = Modifier.size(width = 470.dp, height = 300.dp),
        )

        Column(
            modifier = Modifier
                .height(300.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaText(text = "站点：$selectedSourceLabel")
            }
            val a = actor.trim()
            if (a.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetaText(text = "主演：$a")
                }
            }

            if (showPlayInfo) {
                val lineLabel = playInfoLineLabel.trim()
                if (lineLabel.isNotBlank()) {
                    MetaText(text = "网盘源：$lineLabel")
                }
                val u = playInfoUrl.trim()
                if (u.isNotBlank()) {
                    MetaText(text = "播放地址：$u")
                }
            }

            val desc = content.trim().replace("\n", " ").replace("\t", " ")
            if (desc.isNotBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallActionButton(
                    label = if (isFavorite) "已收藏" else "收藏",
                    icon = Icons.Outlined.FavoriteBorder,
                    accent = accent,
                    onClick = onToggleFavorite,
                )
                if (showSwitchSource) {
                    SmallActionButton(
                        label = "换源：$selectedSourceLabel",
                        icon = Icons.Outlined.Cached,
                        accent = accent,
                        onClick = onPickSource,
                    )
                }
                SmallActionButton(
                    label = "播放信息",
                    icon = Icons.Outlined.Info,
                    accent = accent,
                    onClick = onTogglePlayInfo,
                )
            }
        }
    }
}

@Composable
private fun EpisodeGrid(
    accent: Color,
    episodes: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        modifier = modifier.fillMaxWidth(),
        columns = GridCells.Fixed(8),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(episodes.size) { idx ->
            val ep = episodes[idx]
            EpisodeChip(
                text = ep,
                accent = accent,
                selected = idx == selectedIndex,
                onClick = { onSelect(idx) },
            )
        }
    }
}

private fun Episode.displayLabel(): String = matchedLabel.ifBlank { rawName }.ifBlank { "正片" }

private fun buildEpisodeList(line: PlayLine, showRaw: Boolean, descending: Boolean): List<Episode> {
    val base =
        if (showRaw) {
            line.episodes.map { it.copy(matchedLabel = it.rawName) }
        } else {
            line.episodes
        }
    val sorted =
        if (descending) base.sortedByDescending { it.number } else base.sortedBy { it.number }
    return sorted
}

private fun sliceEpisodeRange(list: List<Episode>, rangeIndex: Int, size: Int): List<Episode> {
    if (list.isEmpty()) return emptyList()
    val idx = rangeIndex.coerceAtLeast(0)
    val start = idx * size
    if (start >= list.size) return list
    val end = minOf(start + size, list.size)
    return list.subList(start, end)
}

@Composable
private fun EpisodeToolbar(
    lineFlags: List<String>,
    selectedLine: Int,
    onSelectLine: (Int) -> Unit,
    canPickLine: Boolean,
    showRawToggle: Boolean,
    showRaw: Boolean,
    onToggleRaw: () -> Unit,
    descending: Boolean,
    onToggleDescending: () -> Unit,
    ranges: List<String>,
    selectedRange: Int,
    onSelectRange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canPickLine) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(lineFlags.size) { idx ->
                    val label = lineFlags[idx].ifBlank { "来源${idx + 1}" }
                    SmallPill(
                        text = label,
                        selected = idx == selectedLine,
                        onClick = { onSelectLine(idx) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showRawToggle) {
                SmallPill(
                    text = if (showRaw) "返回选集" else "原始列表",
                    selected = showRaw,
                    onClick = onToggleRaw,
                )
            }
            SmallPill(
                text = if (descending) "倒序" else "正序",
                selected = descending,
                onClick = onToggleDescending,
            )
            ranges.forEachIndexed { idx, label ->
                SmallPill(
                    text = label,
                    selected = idx == selectedRange,
                    onClick = { onSelectRange(idx) },
                )
            }
        }
    }
}

@Composable
private fun SmallPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1.0f, label = "pillScale")
    Surface(
        modifier =
            Modifier
                .scale(scale)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .focusable(),
        shape = RoundedCornerShape(999.dp),
        color =
            when {
                selected -> Color(0x22000000)
                focused -> Color(0x28000000)
                else -> Color(0x18000000)
            },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
        )
    }
}

@Composable
private fun PosterCard(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                    RoundedCornerShape(18.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x00000000),
                                Color(0xB3000000),
                            ),
                            startY = 0.55f,
                        ),
                        RoundedCornerShape(18.dp),
                    ),
            )
            Text(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun PlayerBox(
    url: String,
    controller: MeowPlayerController,
    onEnterFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0x11000000),
    ) {
        if (url.trim().isNotBlank()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(onClick = onEnterFullScreen),
            ) {
                // Embedded: hide controllers, click to enter fullscreen
                MeowPlayerView(controller = controller, useController = false, modifier = Modifier.fillMaxSize())

                val err = controller.lastError.trim()
                if (err.isNotBlank()) {
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xAA000000),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            text = "播放失败：$err",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.95f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else if (controller.stateText == "buffering" || controller.stateText == "loading") {
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x66000000),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            text = if (controller.stateText == "buffering") "缓冲中…" else "加载中…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                        RoundedCornerShape(18.dp),
                    ),
            )
        }
    }
}

private fun parsePlayLines(
    playFrom: String,
    playUrl: String,
    cleanRules: List<Regex>,
    episodeRules: List<tv.meowfilm.app.data.CompiledMagicRule>,
): List<PlayLine> {
    val flags = playFrom.split("$$$").map { it.trim() }.filter { it.isNotBlank() }
    val urls = playUrl.split("$$$").map { it.trim() }
    val out = mutableListOf<PlayLine>()

    fun matchEpisode(rawName: String, fallbackNumber: Int): Pair<String, Int> {
        val cleaned = MagicRules.cleanText(rawName, cleanRules)
        val picked = extractEpisodeNumber(cleaned, episodeRules)
        val label = if (picked > 0) "第${picked.toString().padStart(2, '0')}集" else cleaned.ifBlank { "正片" }
        val number =
            when {
                picked > 0 -> picked
                else -> fallbackNumber.coerceAtLeast(1)
            }
        return label to number
    }

    for (i in flags.indices) {
        val flag = flags[i]
        val part = urls.getOrNull(i).orEmpty()
        if (flag.isBlank() || part.isBlank()) continue
        val episodes =
            part.split("#")
                .mapIndexedNotNull { epIdx, seg ->
                    val s = seg.trim()
                    if (s.isBlank()) return@mapIndexedNotNull null
                    val idx = s.indexOf('$')
                    if (idx <= 0 || idx >= s.length - 1) return@mapIndexedNotNull null
                    val nameRaw = s.substring(0, idx).trim()
                    val id = s.substring(idx + 1).trim()
                    if (id.isBlank()) return@mapIndexedNotNull null
                    val (matched, num) = matchEpisode(nameRaw, fallbackNumber = epIdx + 1)
                    Episode(rawName = nameRaw, matchedLabel = matched, number = num, flag = flag, id = id)
                }
        if (episodes.isNotEmpty()) out += PlayLine(flag = flag, episodes = episodes)
    }
    return out.ifEmpty {
        // Fallback: some spiders return single list without play_from.
        val part = playUrl.trim()
        if (part.isBlank()) return@ifEmpty emptyList()
        val episodes =
            part.split("#")
                .mapIndexedNotNull { epIdx, seg ->
                    val s = seg.trim()
                    if (s.isBlank()) return@mapIndexedNotNull null
                    val idx = s.indexOf('$')
                    if (idx <= 0 || idx >= s.length - 1) return@mapIndexedNotNull null
                    val nameRaw = s.substring(0, idx).trim()
                    val id = s.substring(idx + 1).trim()
                    if (id.isBlank()) return@mapIndexedNotNull null
                    val (matched, num) = matchEpisode(nameRaw, fallbackNumber = epIdx + 1)
                    Episode(
                        rawName = nameRaw,
                        matchedLabel = matched,
                        number = num,
                        flag = flags.firstOrNull().orEmpty().ifBlank { "线路1" },
                        id = id,
                    )
                }
        if (episodes.isNotEmpty()) listOf(PlayLine(flag = flags.firstOrNull().orEmpty().ifBlank { "线路1" }, episodes = episodes)) else emptyList()
    }
}

private fun extractEpisodeNumber(
    text: String,
    rules: List<tv.meowfilm.app.data.CompiledMagicRule>,
): Int {
    val s = text.trim()
    if (s.isBlank()) return 0

    // Prefer explicit "SxxEyy"
    Regex("""(?:S(\d{1,2}))?\s*E(\d{1,5})""", RegexOption.IGNORE_CASE).find(s)?.let { m ->
        return m.groupValues.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 99999) ?: 0
    }

    // Chinese "第xx集/话/回"
    Regex("""第\s*(\d{1,5})\s*(?:集|话|回)""").find(s)?.let { m ->
        return m.groupValues.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 99999) ?: 0
    }

    for (r in rules) {
        val m = r.regex.find(s) ?: continue
        if (!r.replace.isNullOrBlank()) {
            val normalized = runCatching { s.replace(r.regex, r.replace) }.getOrDefault("")
            Regex("""(?:S(\d{1,2}))?\s*E(\d{1,5})""", RegexOption.IGNORE_CASE).find(normalized)?.let { mm ->
                return mm.groupValues.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 99999) ?: 0
            }
        }
        val picked =
            when {
                m.groupValues.size > 2 && m.groupValues[2].isNotBlank() -> m.groupValues[2]
                m.groupValues.size > 1 && m.groupValues[1].isNotBlank() -> m.groupValues[1]
                else -> m.value
            }
        val digits = picked.replace(Regex("\\D+"), "")
        val n = digits.toIntOrNull() ?: continue
        if (n in 1..99999) return n
    }

    // Loose fallback: first number in text
    Regex("""(\d{1,5})""").find(s)?.let { m ->
        return m.groupValues.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 99999) ?: 0
    }
    return 0
}

@Composable
private fun EpisodeChip(
    text: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1.0f, label = "epScale")
    val border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

    Surface(
        modifier = modifier
            .height(44.dp)
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
        tonalElevation = if (focused) 8.dp else 0.dp,
        shadowElevation = if (focused) 12.dp else 0.dp,
        border = border,
        color =
            when {
                selected -> accent.copy(alpha = 0.25f)
                focused -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (focused) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                maxLines = 1,
            )
        }
    }
}

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

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SourcePickerDialog(
    sources: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "选择线路", style = MaterialTheme.typography.titleLarge)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(sources.size) { idx ->
                        SourceOptionPill(
                            text = sources[idx],
                            selected = idx == selectedIndex,
                            onClick = { onSelect(idx) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceOptionPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1.0f, label = "srcOptScale")
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
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color =
                if (selected) MaterialTheme.colorScheme.onSecondary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1.0f, label = "smallActionScale")
    val border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

    Surface(
        modifier = Modifier
            .width(120.dp)
            .height(40.dp)
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
        tonalElevation = if (focused) 8.dp else 0.dp,
        shadowElevation = if (focused) 12.dp else 0.dp,
        border = border,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = icon,
                contentDescription = label,
                tint = accent.copy(alpha = 0.95f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
