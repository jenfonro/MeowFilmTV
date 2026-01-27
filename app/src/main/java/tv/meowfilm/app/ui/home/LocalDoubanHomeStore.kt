package tv.meowfilm.app.ui.home

import androidx.compose.runtime.staticCompositionLocalOf

val LocalDoubanHomeStore =
    staticCompositionLocalOf<DoubanHomeStore> {
        error("DoubanHomeStore not provided")
    }

