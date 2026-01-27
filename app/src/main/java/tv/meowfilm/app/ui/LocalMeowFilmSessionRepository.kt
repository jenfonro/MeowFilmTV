package tv.meowfilm.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import tv.meowfilm.app.data.MeowFilmSessionRepository

val LocalMeowFilmSessionRepository = staticCompositionLocalOf<MeowFilmSessionRepository> {
    error("LocalMeowFilmSessionRepository not provided")
}

