package tv.meowfilm.app.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.meowfilm.app.data.HttpClient
import tv.meowfilm.app.data.WallpaperStore
import tv.meowfilm.app.BuildConfig
import tv.meowfilm.app.ui.LocalAppSettingsRepository
import tv.meowfilm.app.ui.components.MeowFilmBackground

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val repo = LocalAppSettingsRepository.current
    val ctx = LocalContext.current
    val settings = repo.settings
    val scope = rememberCoroutineScope()
    var wallpaperStatus by remember { mutableStateOf("") }

    val page = rememberSaveable { mutableIntStateOf(0) } // 0=main, 1=server, 2=appearance, 3=douban
    var editing by rememberSaveable { mutableStateOf<EditDialog?>(null) }
    var picking by rememberSaveable { mutableStateOf<PickDialog?>(null) }

    BackHandler {
        if (page.intValue != 0) {
            page.intValue = 0
        } else {
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MeowFilmBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, start = 26.dp, end = 26.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text =
                    when (page.intValue) {
                        1 -> "MeowFilm 服务器设置"
                        2 -> "外观设置"
                        3 -> "豆瓣数据设置"
                        else -> "设置"
                    },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (wallpaperStatus.isNotBlank()) {
                Text(
                    text = wallpaperStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val rows =
                    if (page.intValue == 0) {
                        listOf(
                            SettingRow(
                                title = "外观设置",
                                value = "",
                                onClick = { page.intValue = 2 },
                            ),
                            SettingRow(
                                title = "豆瓣数据设置",
                                value = "",
                                onClick = { page.intValue = 3 },
                            ),
                            SettingRow(
                                title = "MeowFilm 服务器设置",
                                value = "",
                                onClick = { page.intValue = 1 },
                            ),
                            SettingRow(
                                title = "重启",
                                value = "",
                                onClick = {
                                    val a = ctx as? Activity
                                    a?.recreate()
                                },
                            ),
                            SettingRow(
                                title = "版本",
                                value = displayVersion(BuildConfig.VERSION_NAME),
                                onClick = {},
                                enabled = false,
                            ),
                        )
                    } else if (page.intValue == 1) {
                        listOf(
                            SettingRow(
                                title = "服务器地址",
                                value = settings.serverUrl.ifBlank { "未设置" },
                                onClick = {
                                    editing =
                                        EditDialog(
                                            title = "服务器地址",
                                            placeholder = "例如：http://192.168.1.10:8080",
                                            value = settings.serverUrl,
                                            onConfirm = { repo.setServerUrl(it) },
                                        )
                                },
                            ),
                            SettingRow(
                                title = "用户名",
                                value = settings.serverUsername.ifBlank { "未设置" },
                                onClick = {
                                    editing =
                                        EditDialog(
                                            title = "用户名",
                                            placeholder = "请输入用户名",
                                            value = settings.serverUsername,
                                            onConfirm = { repo.setServerUsername(it) },
                                        )
                                },
                            ),
                            SettingRow(
                                title = "密码",
                                value = if (settings.serverPassword.isBlank()) "未设置" else "已设置",
                                onClick = {
                                    editing =
                                        EditDialog(
                                            title = "密码",
                                            placeholder = "请输入密码",
                                            value = settings.serverPassword,
                                            onConfirm = { repo.setServerPassword(it) },
                                        )
                                },
                            ),
                        )
                    } else if (page.intValue == 2) {
                        listOf(
                            SettingRow(
                                title = "主题",
                                value = labelForThemeMode(settings.themeMode),
                                onClick = {
                                    picking =
                                        PickDialog(
                                            title = "主题",
                                            options = themeOptions(),
                                            selected = settings.themeMode,
                                            onSelect = { repo.setThemeMode(it) },
                                        )
                                },
                            ),
                            SettingRow(
                                title = "壁纸链接",
                                value = settings.wallpaperUrl.ifBlank { "默认" },
                                onClick = {
                                    editing =
                                        EditDialog(
                                            title = "壁纸链接",
                                            placeholder = "https://example.com/wallpaper.jpg",
                                            value = settings.wallpaperUrl,
                                            onConfirm = { url ->
                                                val v = url.trim()
                                                repo.setWallpaperUrl(v)
                                                if (v.isBlank()) {
                                                    repo.setWallpaperLocalPath("")
                                                    wallpaperStatus = "已恢复默认壁纸"
                                                } else {
                                                    wallpaperStatus = "壁纸下载中…"
                                                    scope.launch {
                                                        val result =
                                                            withContext(Dispatchers.IO) {
                                                                runCatching {
                                                                    val bytes = HttpClient.getBytes(v)
                                                                    val file = WallpaperStore.wallpaperFile(ctx.applicationContext)
                                                                    file.outputStream().use { it.write(bytes) }
                                                                    file.absolutePath
                                                                }
                                                            }
                                                        result
                                                            .onSuccess { path ->
                                                                repo.setWallpaperLocalPath(path)
                                                                wallpaperStatus = "壁纸已更新"
                                                            }
                                                            .onFailure {
                                                                repo.setWallpaperLocalPath("")
                                                                wallpaperStatus = "壁纸下载失败"
                                                            }
                                                    }
                                                }
                                            },
                                        )
                                },
                            ),
                        )
                    } else {
                        listOf(
                            SettingRow(
                                title = "豆瓣数据代理",
                                value = labelForDoubanDataProxy(settings.doubanDataProxy),
                                onClick = {
                                    picking =
                                        PickDialog(
                                            title = "豆瓣数据代理",
                                            options = doubanDataProxyOptions(),
                                            selected = settings.doubanDataProxy,
                                            onSelect = { mode ->
                                                if (mode == "custom") {
                                                    editing =
                                                        EditDialog(
                                                            title = "豆瓣代理地址",
                                                            placeholder = "例如：https://proxy.example.com/fetch?url=",
                                                            value = settings.doubanDataCustom,
                                                            onConfirm = { repo.setDoubanDataProxy("custom", it) },
                                                        )
                                                } else {
                                                    repo.setDoubanDataProxy(mode)
                                                }
                                            },
                                        )
                                },
                            ),
                            SettingRow(
                                title = "豆瓣图片代理",
                                value = labelForDoubanImgProxy(settings.doubanImgProxy),
                                onClick = {
                                    picking =
                                        PickDialog(
                                            title = "豆瓣图片代理",
                                            options = doubanImgProxyOptions(),
                                            selected = settings.doubanImgProxy,
                                            onSelect = { mode ->
                                                if (mode == "custom") {
                                                    editing =
                                                        EditDialog(
                                                            title = "豆瓣图片代理地址",
                                                            placeholder = "例如：https://proxy.example.com/fetch?url=",
                                                            value = settings.doubanImgCustom,
                                                            onConfirm = { repo.setDoubanImgProxy("custom", it) },
                                                        )
                                                } else {
                                                    repo.setDoubanImgProxy(mode)
                                                }
                                            },
                                        )
                                },
                            ),
                        )
                    }

                items(rows) { row ->
                    SettingsRowItem(
                        title = row.title,
                        value = row.value,
                        enabled = row.enabled,
                        onClick = row.onClick,
                    )
                }
            }
        }

        val e = editing
        if (e != null) {
            TextInputDialog(
                title = e.title,
                placeholder = e.placeholder,
                initialValue = e.value,
                onDismiss = { editing = null },
                onConfirm = { v ->
                    e.onConfirm(v)
                    editing = null
                },
            )
        }

        val p = picking
        if (p != null) {
            OptionPickerDialog(
                title = p.title,
                options = p.options,
                selected = p.selected,
                onDismiss = { picking = null },
                onSelect = { v ->
                    p.onSelect(v)
                    picking = null
                },
            )
        }
    }
}

private data class SettingRow(
    val title: String,
    val value: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

private data class EditDialog(
    val title: String,
    val placeholder: String,
    val value: String,
    val onConfirm: (String) -> Unit,
)

private data class PickDialog(
    val title: String,
    val options: List<OptionItem>,
    val selected: String,
    val onSelect: (String) -> Unit,
)

private data class OptionItem(
    val value: String,
    val label: String,
)

@Composable
private fun SettingsRowItem(
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1.0f, label = "settingsRowScale")
    val border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled = enabled),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (focused) 10.dp else 0.dp,
        shadowElevation = if (focused) 16.dp else 0.dp,
        border = border,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Clip,
                color =
                    if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            if (value.isNotBlank()) {
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    placeholder: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .width(720.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    DialogButton(label = "取消", onClick = onDismiss)
                    DialogButton(label = "确认", primary = true, onClick = { onConfirm(value) })
                }
            }
        }
    }
}

@Composable
private fun OptionPickerDialog(
    title: String,
    options: List<OptionItem>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(options) { opt ->
                        OptionRow(
                            label = opt.label,
                            selected = opt.value == selected,
                            onClick = { onSelect(opt.value) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.03f else 1.0f, label = "optScale")
    val border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (focused) 8.dp else 0.dp,
        shadowElevation = if (focused) 12.dp else 0.dp,
        border = border,
        color =
            when {
                selected -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (selected) MaterialTheme.colorScheme.onSecondary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1.0f, label = "dlgBtnScale")
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
        color =
            when {
                primary -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
    ) {
        Box(
            modifier = Modifier
                .height(44.dp)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (primary) MaterialTheme.colorScheme.onSecondary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            )
        }
    }
}

private fun doubanDataProxyOptions(): List<OptionItem> =
    listOf(
        OptionItem("direct", "直连（直接请求豆瓣）"),
        OptionItem("cors", "Cors Proxy By Zwei"),
        OptionItem("cdn-tx", "豆瓣 CDN By CMLiussss（腾讯云）"),
        OptionItem("cdn-ali", "豆瓣 CDN By CMLiussss（阿里云）"),
        OptionItem("custom", "自定义代理"),
    )

private fun doubanImgProxyOptions(): List<OptionItem> =
    listOf(
        OptionItem("direct-browser", "直连（直接请求豆瓣）"),
        OptionItem("server-proxy", "服务器代理（由服务器代理请求豆瓣）"),
        OptionItem("douban-cdn-ali", "豆瓣官方精品 CDN（阿里云）"),
        OptionItem("cdn-tx", "豆瓣 CDN By CMLiussss（腾讯云）"),
        OptionItem("cdn-ali", "豆瓣 CDN By CMLiussss（阿里云）"),
        OptionItem("custom", "自定义代理"),
    )

private fun labelForDoubanDataProxy(mode: String): String =
    when (mode.trim()) {
        "cors" -> "Cors Proxy By Zwei"
        "cdn-tx", "cmliussss-cdn-tencent" -> "CMLiussss CDN（腾讯云）"
        "cdn-ali", "cmliussss-cdn-ali" -> "CMLiussss CDN（阿里云）"
        "custom" -> "自定义"
        else -> "直连"
    }

private fun labelForDoubanImgProxy(mode: String): String =
    when (mode.trim()) {
        "server-proxy" -> "服务器代理"
        "douban-cdn-ali", "img3" -> "官方精品 CDN（阿里云）"
        "cdn-tx", "cmliussss-cdn-tencent" -> "CMLiussss CDN（腾讯云）"
        "cdn-ali", "cmliussss-cdn-ali" -> "CMLiussss CDN（阿里云）"
        "custom" -> "自定义"
        else -> "直连"
    }

private fun displayVersion(versionName: String): String {
    val raw = versionName.trim()
    if (raw.equals("beta", ignoreCase = true)) return "beta"
    if (raw.contains("beta", ignoreCase = true)) return "beta"
    if (raw == "0.1.0") return "beta"
    return raw.removePrefix("v").ifBlank { "beta" }
}

private fun themeOptions(): List<OptionItem> =
    listOf(
        OptionItem("dark", "深色"),
        OptionItem("light", "浅色"),
    )

private fun labelForThemeMode(mode: String): String =
    when (mode.trim().lowercase()) {
        "light" -> "浅色"
        else -> "深色"
    }
