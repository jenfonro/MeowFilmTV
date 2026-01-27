package tv.meowfilm.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope

val LocalAppScope = staticCompositionLocalOf<CoroutineScope> {
    error("LocalAppScope not provided")
}

