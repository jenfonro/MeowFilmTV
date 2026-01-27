package tv.meowfilm.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import tv.meowfilm.app.data.AppSettingsRepository

val LocalAppSettingsRepository =
    staticCompositionLocalOf<AppSettingsRepository> {
        error("AppSettingsRepository not provided")
    }

