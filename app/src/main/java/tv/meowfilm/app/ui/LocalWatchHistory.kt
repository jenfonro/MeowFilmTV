package tv.meowfilm.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import tv.meowfilm.app.data.WatchHistoryRepository

val LocalWatchHistoryRepository =
    staticCompositionLocalOf<WatchHistoryRepository> {
        error("WatchHistoryRepository not provided")
    }

