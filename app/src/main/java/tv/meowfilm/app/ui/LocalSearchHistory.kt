package tv.meowfilm.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import tv.meowfilm.app.data.SearchHistoryRepository

val LocalSearchHistoryRepository =
    staticCompositionLocalOf<SearchHistoryRepository> {
        error("SearchHistoryRepository not provided")
    }

