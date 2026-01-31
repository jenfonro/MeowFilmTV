package tv.meowfilm.app.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tv.meowfilm.app.ui.components.GlassLayer
import tv.meowfilm.app.ui.LocalAppSettingsRepository
import tv.meowfilm.app.data.DoubanClient
import tv.meowfilm.app.ui.LocalSearchHistoryRepository

@Composable
fun SearchScreen(
    onOpenSearchResults: (query: String) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalAppSettingsRepository.current.settings
    var query by rememberSaveable { mutableStateOf("") }
    val historyRepo = LocalSearchHistoryRepository.current
    val history = historyRepo.items
    var hotLoading by remember { mutableStateOf(false) }
    var hotWords by remember {
        mutableStateOf(
            listOf(
                "热门剧集",
                "科幻",
                "动作",
                "喜剧",
                "悬疑",
                "纪录片",
                "4K",
                "2026",
            ),
        )
    }

    var suggestLoading by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(settings.doubanDataProxy, settings.doubanDataCustom) {
        hotLoading = true
        val fetched =
            withContext(Dispatchers.IO) {
                runCatching { DoubanClient.fetchHotWords(settings, limit = 24) }.getOrElse { emptyList() }
            }
        if (fetched.isNotEmpty()) hotWords = fetched
        hotLoading = false
    }

    LaunchedEffect(query, settings.doubanDataProxy, settings.doubanDataCustom) {
        val q = query.trim()
        if (q.isBlank()) {
            suggestions = emptyList()
            suggestLoading = false
            return@LaunchedEffect
        }
        suggestLoading = true
        delay(220)
        val fetched =
            withContext(Dispatchers.IO) {
                runCatching { DoubanClient.fetchSuggestWords(q, settings, limit = 18) }.getOrElse { emptyList() }
            }
        if (q == query.trim()) {
            suggestions = fetched
        }
        suggestLoading = false
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WordsPanel(
                    title = "历史",
                    emptyText = "暂无历史",
                    words = history,
                    onPick = { w ->
                        query = w
                        val q = w.trim()
                        if (q.isNotBlank()) {
                            historyRepo.add(q)
                            onOpenSearchResults(q)
                        }
                    },
                    onContentFocus = onContentFocus,
                    modifier = Modifier.width(220.dp),
                )

                SearchLeftPanel(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {
                        val q = query.trim()
                        if (q.isNotBlank()) {
                            historyRepo.add(q)
                            onOpenSearchResults(q)
                        }
                    },
                    onClear = {
                        query = ""
                    },
                    onContentFocus = onContentFocus,
                    modifier = Modifier.width(520.dp),
                )

                if (query.trim().isBlank()) {
                    WordsPanel(
                        title = "热搜",
                        emptyText = if (hotLoading) "加载中…" else "暂无热搜",
                        words = hotWords,
                        onPick = { w ->
                            query = w
                            val q = w.trim()
                            if (q.isNotBlank()) {
                                historyRepo.add(q)
                                onOpenSearchResults(q)
                            }
                        },
                        onContentFocus = onContentFocus,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    WordsPanel(
                        title = "建议",
                        emptyText = if (suggestLoading) "加载中…" else "暂无建议",
                        words = suggestions,
                        onPick = { w ->
                            query = w
                            val q = w.trim()
                            if (q.isNotBlank()) {
                                historyRepo.add(q)
                                onOpenSearchResults(q)
                            }
                        },
                        onContentFocus = onContentFocus,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            GlassLayer(
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun SearchLeftPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Column(
        modifier = modifier
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassLayer(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isLight) 0.45f else 0.22f),
                    RoundedCornerShape(18.dp),
                ),
            tint = if (isLight) Color(0x26FFFFFF) else Color(0x4D0B0F14),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                SearchInput(
                    value = query,
                    onValueChange = onQueryChange,
                    onContentFocus = onContentFocus,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "搜索",
                onClick = onSearch,
                onContentFocus = onContentFocus,
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                label = "清空",
                onClick = onClear,
                icon = Icons.Outlined.Clear,
                onContentFocus = onContentFocus,
            )
        }

        SearchKeyboard(
            onInput = { ch -> onValueChange(query, ch, onQueryChange) },
            onBackspace = { onQueryChange(query.dropLast(1)) },
            onSpace = { onQueryChange(query + " ") },
            onContentFocus = onContentFocus,
        )
    }
}

@Composable
private fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    onContentFocus: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border =
        if (focused) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (isLight) 0.22f else 0.18f))
        }
    val focusRequester = remember { FocusRequester() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { f ->
                focused = f.isFocused
                if (f.isFocused) focusRequester.requestFocus()
                if (f.isFocused) onContentFocus()
            }
            .clickable { focusRequester.requestFocus() }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && e.key == Key.Backspace) {
                    if (value.isNotEmpty()) onValueChange(value.dropLast(1))
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isLight) 0.65f else 0.42f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = "搜索…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle =
                        TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                            fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                            lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                        ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                )
            }

            if (value.isNotEmpty()) {
                Spacer(modifier = Modifier.size(10.dp))
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.Outlined.Backspace,
                    contentDescription = "Backspace",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onContentFocus: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val border =
        if (focused) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (isLight) 0.22f else 0.18f))
        }
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier
            .height(54.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onContentFocus()
            }
            .clickable(onClick = onClick)
            .focusable(),
        shape = shape,
        tonalElevation = if (focused) 8.dp else 2.dp,
        shadowElevation = if (focused) 12.dp else 0.dp,
        border = border,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isLight) 0.60f else 0.55f),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SearchKeyboard(
    onInput: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys =
        remember {
            buildList {
                addAll("1234567890".toList())
                addAll("QWERTYUIOP".toList())
                addAll("ASDFGHJKL".toList())
                addAll("ZXCVBNM".toList())
            }
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(10),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height((54 * 4 + 10 * 3).dp),
            ) {
                items(keys) { ch ->
                    KeyButton(
                        text = ch.toString(),
                        onClick = { onInput(ch) },
                        onContentFocus = onContentFocus,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyButton(
                    modifier = Modifier.weight(1f),
                    text = "空格",
                    onClick = onSpace,
                    onContentFocus = onContentFocus,
                )
                KeyButton(
                    modifier = Modifier.width(120.dp),
                    text = "删除",
                    onClick = onBackspace,
                    onContentFocus = onContentFocus,
                )
            }
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    onClick: () -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    val shape = RoundedCornerShape(14.dp)

    Surface(
        modifier = modifier
            .height(54.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onContentFocus()
            }
            .clickable(onClick = onClick)
            .focusable(),
        shape = shape,
        tonalElevation = if (focused) 8.dp else 1.dp,
        shadowElevation = if (focused) 12.dp else 0.dp,
        border = border,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun WordsPanel(
    title: String,
    emptyText: String,
    words: List<String>,
    onPick: (String) -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
        ) {
            if (words.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(words) { w ->
                        WordItem(
                            text = w,
                            onClick = { onPick(w) },
                            onContentFocus = onContentFocus,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WordItem(
    text: String,
    onClick: () -> Unit,
    onContentFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onContentFocus()
            }
            .clickable(onClick = onClick)
            .focusable(),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = if (focused) 8.dp else 1.dp,
        shadowElevation = if (focused) 12.dp else 0.dp,
        border = border,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

private fun onValueChange(current: String, ch: Char, onQueryChange: (String) -> Unit) {
    if (current.length >= 30) return
    onQueryChange(current + ch)
}
