package tv.meowfilm.app.ui.search

import androidx.compose.runtime.staticCompositionLocalOf

val LocalSearchResultsStore = staticCompositionLocalOf<SearchResultsStore> {
    error("LocalSearchResultsStore not provided")
}

