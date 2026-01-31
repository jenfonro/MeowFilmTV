package tv.meowfilm.app.data

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class AppSettings(
    val serverUrl: String = "",
    val serverUsername: String = "",
    val serverPassword: String = "",
    val themeMode: String = "dark", // dark | light
    val wallpaperUrl: String = "",
    val wallpaperLocalPath: String = "",
    val doubanDataProxy: String = "cdn-tx",
    val doubanDataCustom: String = "",
    val doubanImgProxy: String = "cdn-tx",
    val doubanImgCustom: String = "",
)

@Stable
class AppSettingsRepository(
    private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var settings by mutableStateOf(load())
        private set

    fun setServerUrl(url: String) {
        val normalized = url.trim().removeSuffix("/")
        settings = settings.copy(serverUrl = normalized)
        prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
    }

    fun setServerUsername(username: String) {
        val normalized = username.trim()
        settings = settings.copy(serverUsername = normalized)
        prefs.edit().putString(KEY_SERVER_USERNAME, normalized).apply()
    }

    fun setServerPassword(password: String) {
        val normalized = password
        settings = settings.copy(serverPassword = normalized)
        prefs.edit().putString(KEY_SERVER_PASSWORD, normalized).apply()
    }

    fun setThemeMode(mode: String) {
        val m =
            when (mode.trim().lowercase()) {
                "light" -> "light"
                else -> "dark"
            }
        settings = settings.copy(themeMode = m)
        prefs.edit().putString(KEY_THEME_MODE, m).apply()
    }

    fun setWallpaperUrl(url: String) {
        val normalized = url.trim()
        settings = settings.copy(wallpaperUrl = normalized)
        prefs.edit().putString(KEY_WALLPAPER_URL, normalized).apply()
    }

    fun setWallpaperLocalPath(path: String) {
        val normalized = path.trim()
        settings = settings.copy(wallpaperLocalPath = normalized)
        prefs.edit().putString(KEY_WALLPAPER_LOCAL_PATH, normalized).apply()
    }

    fun setDoubanDataProxy(mode: String, custom: String? = null) {
        val m = mode.trim().ifBlank { "cdn-tx" }
        val c = (custom ?: settings.doubanDataCustom).trim()
        settings = settings.copy(doubanDataProxy = m, doubanDataCustom = c)
        prefs.edit()
            .putString(KEY_DOUBAN_DATA_PROXY, m)
            .putString(KEY_DOUBAN_DATA_CUSTOM, c)
            .apply()
    }

    fun setDoubanImgProxy(mode: String, custom: String? = null) {
        val m = mode.trim().ifBlank { "cdn-tx" }
        val c = (custom ?: settings.doubanImgCustom).trim()
        settings = settings.copy(doubanImgProxy = m, doubanImgCustom = c)
        prefs.edit()
            .putString(KEY_DOUBAN_IMG_PROXY, m)
            .putString(KEY_DOUBAN_IMG_CUSTOM, c)
            .apply()
    }

    private fun load(): AppSettings =
        AppSettings(
            serverUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL,
            serverUsername = prefs.getString(KEY_SERVER_USERNAME, DEFAULT_SERVER_USERNAME) ?: DEFAULT_SERVER_USERNAME,
            serverPassword = prefs.getString(KEY_SERVER_PASSWORD, DEFAULT_SERVER_PASSWORD) ?: DEFAULT_SERVER_PASSWORD,
            themeMode = prefs.getString(KEY_THEME_MODE, "dark") ?: "dark",
            wallpaperUrl = prefs.getString(KEY_WALLPAPER_URL, "") ?: "",
            wallpaperLocalPath = prefs.getString(KEY_WALLPAPER_LOCAL_PATH, "") ?: "",
            doubanDataProxy = prefs.getString(KEY_DOUBAN_DATA_PROXY, "cdn-tx") ?: "cdn-tx",
            doubanDataCustom = prefs.getString(KEY_DOUBAN_DATA_CUSTOM, "") ?: "",
            doubanImgProxy = prefs.getString(KEY_DOUBAN_IMG_PROXY, "cdn-tx") ?: "cdn-tx",
            doubanImgCustom = prefs.getString(KEY_DOUBAN_IMG_CUSTOM, "") ?: "",
        )

    private companion object {
        private const val PREFS_NAME = "meowfilm_settings"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_USERNAME = "server_username"
        private const val KEY_SERVER_PASSWORD = "server_password"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_WALLPAPER_URL = "wallpaper_url"
        private const val KEY_WALLPAPER_LOCAL_PATH = "wallpaper_local_path"
        private const val KEY_DOUBAN_DATA_PROXY = "douban_data_proxy"
        private const val KEY_DOUBAN_DATA_CUSTOM = "douban_data_custom"
        private const val KEY_DOUBAN_IMG_PROXY = "douban_img_proxy"
        private const val KEY_DOUBAN_IMG_CUSTOM = "douban_img_custom"

        private const val DEFAULT_SERVER_URL = "https://movie.zelt.cn"
        private const val DEFAULT_SERVER_USERNAME = "zel"
        private const val DEFAULT_SERVER_PASSWORD = "87981534"
    }
}
