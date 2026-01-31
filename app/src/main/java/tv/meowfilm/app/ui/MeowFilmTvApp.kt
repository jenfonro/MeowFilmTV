package tv.meowfilm.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import tv.meowfilm.app.data.AppSettingsRepository
import tv.meowfilm.app.data.MeowFilmSessionRepository
import tv.meowfilm.app.data.SearchHistoryRepository
import tv.meowfilm.app.data.WatchHistoryRepository
import tv.meowfilm.app.ui.LocalWatchHistoryRepository
import tv.meowfilm.app.ui.LocalMeowFilmSessionRepository
import tv.meowfilm.app.ui.LocalSearchHistoryRepository
import tv.meowfilm.app.ui.screens.MainScreen
import tv.meowfilm.app.ui.screens.DetailScreen
import tv.meowfilm.app.ui.screens.FavoritesScreen
import tv.meowfilm.app.ui.screens.HistoryScreen
import tv.meowfilm.app.ui.screens.PlayerPlaceholderScreen
import tv.meowfilm.app.ui.screens.SearchResultsScreen
import tv.meowfilm.app.ui.screens.SettingsScreen
import tv.meowfilm.app.ui.home.DoubanHomeStore
import tv.meowfilm.app.ui.home.LocalDoubanHomeStore
import tv.meowfilm.app.ui.meowfilm.LocalMeowFilmStore
import tv.meowfilm.app.ui.meowfilm.MeowFilmStore
import tv.meowfilm.app.data.MeowFilmHistoryClient
import org.json.JSONObject
import tv.meowfilm.app.ui.LocalAppScope
import tv.meowfilm.app.ui.search.LocalSearchResultsStore
import tv.meowfilm.app.ui.search.SearchResultsStore

@Composable
fun MeowFilmTvApp() {
    val context = LocalContext.current.applicationContext
    val settingsRepo = remember { AppSettingsRepository(context) }
    val historyRepo = remember { WatchHistoryRepository(context) }
    val searchHistoryRepo = remember { SearchHistoryRepository(context) }
    val doubanHomeStore = remember { DoubanHomeStore() }
    val meowFilmStore = remember { MeowFilmStore() }
    val meowFilmSessionRepo = remember { MeowFilmSessionRepository(context) }
    val searchResultsStore = remember { SearchResultsStore() }
    val appScope = rememberCoroutineScope()

    CompositionLocalProvider(
        LocalAppSettingsRepository provides settingsRepo,
        LocalWatchHistoryRepository provides historyRepo,
        LocalSearchHistoryRepository provides searchHistoryRepo,
        LocalDoubanHomeStore provides doubanHomeStore,
        LocalMeowFilmStore provides meowFilmStore,
        LocalMeowFilmSessionRepository provides meowFilmSessionRepo,
        LocalSearchResultsStore provides searchResultsStore,
        LocalAppScope provides appScope,
    ) {
        val settings = settingsRepo.settings
        LaunchedEffect(settings.serverUrl, settings.serverUsername, settings.serverPassword) {
            meowFilmStore.ensureSession(scope = appScope, settings = settings, sessionRepo = meowFilmSessionRepo)
        }

        LaunchedEffect(meowFilmStore.bootstrap, meowFilmStore.session, settings.serverUrl) {
            val sess = meowFilmStore.session
            if (meowFilmStore.bootstrap?.authenticated != true || sess == null) return@LaunchedEffect
            val serverUrl = settings.serverUrl.trim()
            if (serverUrl.isBlank()) return@LaunchedEffect

            // 1) Pull server history and merge locally
            runCatching {
                val serverItems = MeowFilmHistoryClient.fetchPlayHistory(serverUrl, sess, limit = 50)
                historyRepo.mergeFromServer(serverItems)
                historyRepo.saveLastServerSyncAt(System.currentTimeMillis())

                // 2) Push local newer/offline items
                val serverMap = serverItems.associateBy { it.contentKey.ifBlank { it.videoTitle } }
                historyRepo.items
                    .filter { it.pendingSync && it.siteKey.isNotBlank() && it.spiderApi.isNotBlank() && it.videoId.isNotBlank() }
                    .forEach { local ->
                        val server = serverMap[local.contentKey] ?: serverMap[local.title]
                        val serverMs = (server?.updatedAtSec ?: 0L) * 1000L
                        if (local.updatedAt > serverMs) {
                            val payload =
                                JSONObject()
                                    .put("siteKey", local.siteKey)
                                    .put("siteName", local.siteName)
                                    .put("spiderApi", local.spiderApi)
                                    .put("videoId", local.videoId)
                                    .put("videoTitle", local.title)
                                    .put("videoPoster", local.posterUrl)
                                    .put("videoRemark", "")
                                    .put("panLabel", "")
                                    .put("playFlag", local.playFlag)
                                    .put("episodeIndex", local.episodeIndex)
                                    .put("episodeName", local.episodeName)
                            MeowFilmHistoryClient.postPlayHistory(serverUrl, sess, payload)
                            historyRepo.markSynced(local.contentKey)
                        } else {
                            historyRepo.markSynced(local.contentKey)
                        }
                    }
            }
        }

        MeowFilmTvTheme(themeMode = settingsRepo.settings.themeMode) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            onOpenDetail = { payload ->
                                nav.navigate("detail?payload=${encodeRouteArg(payload)}")
                            },
                            onOpenSearchResults = { query ->
                                nav.navigate("searchResults?query=${encodeRouteArg(query)}")
                            },
                            onOpenFavorites = { nav.navigate("favorites") },
                            onOpenHistory = { nav.navigate("history") },
                            onOpenSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("searchResults?query={query}") { backStack ->
                        val q = decodeRouteArg(backStack.arguments?.getString("query") ?: "")
                        SearchResultsScreen(
                            query = q,
                            onBack = {
                                nav.navigate("main") {
                                    popUpTo("main") { inclusive = true }
                                }
                            },
                            onOpenDetail = { payload ->
                                nav.navigate("detail?payload=${encodeRouteArg(payload)}")
                            },
                        )
                    }
                    composable("detail?payload={payload}") { backStack ->
                        val p = decodeRouteArg(backStack.arguments?.getString("payload") ?: "")
                        val decoded = NavPayload.decode(p) ?: VideoPayload(title = "未知内容")
                        DetailScreen(
                            payload = decoded,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("player?title={title}") { backStack ->
                        val t = backStack.arguments?.getString("title") ?: ""
                        PlayerPlaceholderScreen(
                            title = decodeRouteArg(t),
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            onBack = { nav.popBackStack() },
                            onOpenFavorites = {
                                nav.navigate("favorites") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenDetail = { payload ->
                                nav.navigate("detail?payload=${encodeRouteArg(payload)}")
                            },
                        )
                    }
                    composable("favorites") {
                        FavoritesScreen(
                            onBack = { nav.popBackStack() },
                            onOpenHistory = {
                                nav.navigate("history") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenDetail = { payload ->
                                nav.navigate("detail?payload=${encodeRouteArg(payload)}")
                            },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

private fun decodeRouteArg(v: String): String =
    Uri.decode(v)

private fun encodeRouteArg(v: String): String =
    Uri.encode(v)
