package tv.meowfilm.app.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import tv.meowfilm.app.ui.LocalAppSettingsRepository
import tv.meowfilm.app.ui.components.MeowFilmBackground
import tv.meowfilm.app.ui.components.TopTabsBar
import tv.meowfilm.app.ui.home.LocalDoubanHomeStore
import tv.meowfilm.app.ui.meowfilm.LocalMeowFilmStore

@Composable
fun MainScreen(
    onOpenDetail: (payload: String) -> Unit,
    onOpenSearchResults: (query: String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tabs = listOf("首页", "搜索")
    val selectedTab = rememberSaveable { mutableIntStateOf(0) }
    val selectedCategory = rememberSaveable { mutableIntStateOf(0) }
    var topBarVisible by rememberSaveable { mutableIntStateOf(1) } // 1=true 0=false
    var homeCategoryFocused by rememberSaveable { mutableIntStateOf(0) } // 1=true 0=false
    val categoryFocusRequesters = remember { List(4) { FocusRequester() } }

    val settings = LocalAppSettingsRepository.current.settings
    val doubanStore = LocalDoubanHomeStore.current
    val meowFilmStore = LocalMeowFilmStore.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(
        settings.doubanDataProxy,
        settings.doubanDataCustom,
        settings.doubanImgProxy,
        settings.doubanImgCustom,
    ) {
        doubanStore.ensurePrefetch(scope = scope, settings = settings)
    }

    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val lastBackAt = rememberSaveable { mutableStateOf(0L) }

    BackHandler {
        if (selectedTab.intValue != 0) {
            selectedTab.intValue = 0
            topBarVisible = 1
            homeCategoryFocused = 1
            categoryFocusRequesters.getOrNull(selectedCategory.intValue)?.requestFocus()
            lastBackAt.value = 0L
            return@BackHandler
        }

        if (homeCategoryFocused == 0) {
            topBarVisible = 1
            homeCategoryFocused = 1
            categoryFocusRequesters.getOrNull(selectedCategory.intValue)?.requestFocus()
            lastBackAt.value = 0L
            return@BackHandler
        }

        val now = System.currentTimeMillis()
        if (now - lastBackAt.value <= 1200L) {
            activity?.finish()
        } else {
            lastBackAt.value = now
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MeowFilmBackground()

        val isTopBarVisible = topBarVisible == 1
        val barAlpha by animateFloatAsState(if (isTopBarVisible) 1f else 0f, label = "barAlpha")
        val barOffsetY by animateDpAsState(if (isTopBarVisible) 0.dp else (-70).dp, animationSpec = tween(180), label = "barOffset")
        val contentInset by animateDpAsState(if (isTopBarVisible) 52.dp else 0.dp, animationSpec = tween(180), label = "contentInset")
        val density = LocalDensity.current

        TopTabsBar(
            modifier =
                Modifier
                    .padding(top = 14.dp, start = 26.dp, end = 26.dp)
                    .clipToBounds()
                    .graphicsLayer {
                        translationY = with(density) { barOffsetY.toPx() }
                        alpha = barAlpha
                    },
            tabs = tabs,
            selectedIndex = selectedTab.intValue,
            onSelect = { selectedTab.intValue = it },
            username = if (meowFilmStore.bootstrap?.authenticated == true) meowFilmStore.tvUser() else "",
            onOpenFavorites = onOpenFavorites,
            onOpenHistory = onOpenHistory,
            onOpenSettings = onOpenSettings,
            onFocusInTopBar = { topBarVisible = 1 },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 14.dp + contentInset),
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            when (selectedTab.intValue) {
                0 ->
                    HomeTabContent(
                        selectedCategoryIndex = selectedCategory.intValue,
                        onSelectCategory = { selectedCategory.intValue = it },
                        onOpenDetail = onOpenDetail,
                        onOpenSearchResults = onOpenSearchResults,
                        onTopAreaFocus = { topBarVisible = 1 },
                        onContentFocus = {
                            topBarVisible = 0
                            homeCategoryFocused = 0
                        },
                        onCategoryFocusChanged = { focused ->
                            homeCategoryFocused = if (focused) 1 else 0
                        },
                        categoryFocusRequesters = categoryFocusRequesters,
                        modifier = Modifier.fillMaxSize(),
                    )

                else ->
                    SearchScreen(
                        onOpenSearchResults = onOpenSearchResults,
                        onContentFocus = { topBarVisible = 0 },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 26.dp),
                    )
            }
        }
    }
}
