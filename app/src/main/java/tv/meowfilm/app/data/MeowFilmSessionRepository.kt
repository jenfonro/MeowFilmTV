package tv.meowfilm.app.data

import android.content.Context

class MeowFilmSessionRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadToken(serverUrl: String, username: String): String? {
        val base = serverUrl.trim().removeSuffix("/")
        val user = username.trim()
        if (base.isBlank() || user.isBlank()) return null
        val savedBase = prefs.getString(KEY_SERVER_URL, "")?.trim().orEmpty().removeSuffix("/")
        val savedUser = prefs.getString(KEY_USERNAME, "")?.trim().orEmpty()
        if (savedBase != base || savedUser != user) return null
        return prefs.getString(KEY_TOKEN, "")?.trim().takeIf { !it.isNullOrBlank() }
    }

    fun saveToken(serverUrl: String, username: String, token: String) {
        val base = serverUrl.trim().removeSuffix("/")
        val user = username.trim()
        val t = token.trim()
        if (base.isBlank() || user.isBlank() || t.isBlank()) return
        prefs.edit()
            .putString(KEY_SERVER_URL, base)
            .putString(KEY_USERNAME, user)
            .putString(KEY_TOKEN, t)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        private const val PREFS_NAME = "meowfilm_session"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_TOKEN = "token"
    }
}

