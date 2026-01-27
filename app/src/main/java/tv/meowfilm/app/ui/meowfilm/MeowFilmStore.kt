package tv.meowfilm.app.ui.meowfilm

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.meowfilm.app.data.AppSettings
import tv.meowfilm.app.data.MeowFilmApiClient
import tv.meowfilm.app.data.MeowFilmBootstrap
import tv.meowfilm.app.data.MeowFilmSite
import tv.meowfilm.app.data.MeowFilmSessionRepository

@Stable
class MeowFilmStore {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set

    var session: MeowFilmApiClient.Session? by mutableStateOf(null)
        private set

    var bootstrap: MeowFilmBootstrap? by mutableStateOf(null)
        private set

    var sites: List<MeowFilmSite> by mutableStateOf(emptyList())
        private set

    private var lastKey: String? = null

    fun ensureSession(scope: CoroutineScope, settings: AppSettings, sessionRepo: MeowFilmSessionRepository) {
        if (loading) return
        val key = settingsKey(settings)
        if (key.isBlank()) {
            loading = false
            error = ""
            session = null
            bootstrap = null
            sites = emptyList()
            lastKey = null
            sessionRepo.clear()
            return
        }

        if (lastKey == key && session != null && bootstrap != null && sites.isNotEmpty()) return

        loading = true
        error = ""
        if (lastKey != key) {
            session = null
            bootstrap = null
            sites = emptyList()
        }
        lastKey = key

        scope.launch {
            ensureSessionBlocking(settings = settings, sessionRepo = sessionRepo)
        }
    }

    suspend fun ensureSessionBlocking(settings: AppSettings, sessionRepo: MeowFilmSessionRepository): Boolean {
        val key = settingsKey(settings)
        if (key.isBlank()) {
            withContext(Dispatchers.Main) {
                loading = false
                error = ""
                session = null
                bootstrap = null
                sites = emptyList()
                lastKey = null
            }
            sessionRepo.clear()
            return false
        }

        val cachedOk = lastKey == key && session != null && bootstrap != null && sites.isNotEmpty()
        if (cachedOk) return true

        withContext(Dispatchers.Main) {
            loading = true
            error = ""
            if (lastKey != key) {
                session = null
                bootstrap = null
                sites = emptyList()
            }
            lastKey = key
        }

        val result =
            withContext(Dispatchers.IO) {
                runCatching {
                    val base = settings.serverUrl.trim()
                    val user = settings.serverUsername.trim()
                    val pwd = settings.serverPassword

                    val savedToken = sessionRepo.loadToken(base, user)
                    if (!savedToken.isNullOrBlank()) {
                        val sess = MeowFilmApiClient.sessionFromToken(savedToken)
                        val boot = MeowFilmApiClient.bootstrap(base, sess, page = "index")
                        if (boot.authenticated) {
                            val siteList = MeowFilmApiClient.userSites(base, sess)
                            return@runCatching Triple(sess, boot, siteList)
                        }
                        sessionRepo.clear()
                    }

                    if (pwd.isBlank()) throw IllegalStateException("未设置密码")
                    val sess = MeowFilmApiClient.login(base, user, pwd)
                    sessionRepo.saveToken(base, user, sess.token)
                    val boot = MeowFilmApiClient.bootstrap(base, sess, page = "index")
                    if (!boot.authenticated) throw IllegalStateException("未登录")
                    val siteList = MeowFilmApiClient.userSites(base, sess)
                    Triple(sess, boot, siteList)
                }
            }

        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                val (sess, boot, siteList) = result.getOrThrow()
                session = sess
                bootstrap = boot
                sites = siteList
                error = ""
            } else {
                session = null
                bootstrap = null
                sites = emptyList()
                error = result.exceptionOrNull()?.message ?: "请求失败"
            }
            loading = false
        }
        return result.isSuccess
    }

    fun catApiBase(): String {
        val b = bootstrap ?: return ""
        val role = b.user?.role.orEmpty()
        val userBase = b.settings.userCatPawOpenApiBase.trim()
        val serverBase = b.settings.catPawOpenApiBase.trim()
        return if (role == "user") userBase else (userBase.ifBlank { serverBase })
    }

    fun tvUser(): String = bootstrap?.user?.username.orEmpty()

    private fun settingsKey(s: AppSettings): String {
        val base = s.serverUrl.trim()
        val u = s.serverUsername.trim()
        if (base.isBlank() || u.isBlank()) return ""
        return listOf(base, u).joinToString("|")
    }
}
