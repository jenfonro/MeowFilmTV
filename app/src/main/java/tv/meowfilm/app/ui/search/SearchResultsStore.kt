package tv.meowfilm.app.ui.search

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.meowfilm.app.data.AppSettings
import tv.meowfilm.app.data.CatPawOpenClient
import tv.meowfilm.app.data.MeowFilmSessionRepository
import tv.meowfilm.app.ui.VideoSourcePayload
import tv.meowfilm.app.ui.meowfilm.MeowFilmStore

@Stable
class SearchResultsStore {
    @Stable
    class State(
        val key: String,
        val query: String,
    ) {
        val items = mutableStateListOf<RawItem>()
        val siteCounts = mutableStateMapOf<String, Int>()
        val siteNames = mutableStateMapOf<String, String>()
        val exactSources = mutableStateMapOf<String, VideoSourcePayload>()

        var loading by mutableStateOf(false)
        var error by mutableStateOf("")

        val done = mutableIntStateOf(0)
        val total = mutableIntStateOf(0)

        var selectedFilterKey by mutableStateOf("ALL")

        var job: Job? = null
    }

    data class RawItem(
        val siteKey: String,
        val siteName: String,
        val spiderApi: String,
        val videoId: String,
        val title: String,
        val poster: String,
        val remark: String,
        val exact: Boolean,
    )

    private val states = mutableStateMapOf<String, State>()

    fun stateFor(settings: AppSettings, query: String): State {
        val q = query.trim()
        val key = buildKey(settings, q)
        return states.getOrPut(key) { State(key = key, query = q) }
    }

    fun ensureSearch(
        scope: CoroutineScope,
        settings: AppSettings,
        meowFilmStore: MeowFilmStore,
        sessionRepo: MeowFilmSessionRepository,
        query: String,
    ) {
        val st = stateFor(settings, query)
        if (st.query.isBlank()) {
            st.loading = false
            st.error = "关键词为空"
            return
        }
        if (st.job != null) return
        if (st.done.intValue > 0 && !st.loading && (st.items.isNotEmpty() || st.error.isNotBlank())) return

        st.job =
            scope.launch {
                st.items.clear()
                st.siteCounts.clear()
                st.siteNames.clear()
                st.exactSources.clear()
                st.error = ""
                st.done.intValue = 0
                st.total.intValue = 0
                st.selectedFilterKey = "ALL"

                val ok = meowFilmStore.ensureSessionBlocking(settings = settings, sessionRepo = sessionRepo)

                val q = st.query
                val boot = meowFilmStore.bootstrap
                val catBase = meowFilmStore.catApiBase().trim()
                val tvUser = meowFilmStore.tvUser().trim()
                val sites = meowFilmStore.sites.filter { it.enabled && it.search }
                if (!ok || boot == null || catBase.isBlank() || tvUser.isBlank() || sites.isEmpty()) {
                    st.error = meowFilmStore.error.ifBlank { "未登录或未配置站点" }
                    st.loading = false
                    st.job = null
                    return@launch
                }

                st.total.intValue = sites.size
                st.loading = true

                val concurrency = boot.settings.searchThreadCount.coerceIn(1, 12)
                val queue = sites.toMutableList()
                val seen = mutableSetOf<String>()
                val errors = mutableListOf<String>()
                val qNorm = normalizeForMatch(q)

                coroutineScope {
                    List(concurrency) {
                        launch(Dispatchers.IO) {
                            while (true) {
                                val site = synchronized(queue) { if (queue.isNotEmpty()) queue.removeAt(0) else null } ?: break
                                val siteKey = site.key
                                val siteName = site.name.ifBlank { siteKey }
                                try {
                                    val got =
                                        CatPawOpenClient.search(
                                            apiBase = catBase,
                                            tvUser = tvUser,
                                            site = site,
                                            keyword = q,
                                            page = 1,
                                        )
                                    withContext(Dispatchers.Main) {
                                        st.siteNames[siteKey] = siteName
                                        st.siteCounts[siteKey] = (st.siteCounts[siteKey] ?: 0) + got.size
                                        got.forEach { g ->
                                            val uniq = "${g.siteKey}::${g.videoId}"
                                            if (!seen.add(uniq)) return@forEach
                                            val exact = qNorm.isNotBlank() && normalizeForMatch(g.title) == qNorm
                                            st.items.add(
                                                RawItem(
                                                    siteKey = g.siteKey,
                                                    siteName = g.siteName.ifBlank { g.siteKey },
                                                    spiderApi = g.spiderApi,
                                                    videoId = g.videoId,
                                                    title = g.title,
                                                    poster = g.poster,
                                                    remark = g.remark,
                                                    exact = exact,
                                                ),
                                            )
                                            if (exact && !st.exactSources.containsKey(g.siteKey)) {
                                                st.exactSources[g.siteKey] =
                                                    VideoSourcePayload(
                                                        siteKey = g.siteKey,
                                                        siteName = g.siteName.ifBlank { g.siteKey },
                                                        spiderApi = g.spiderApi,
                                                        videoId = g.videoId,
                                                    )
                                            }
                                        }
                                    }
                                } catch (e: Throwable) {
                                    synchronized(errors) {
                                        errors.add("${siteName}: ${e.message ?: "error"}")
                                    }
                                } finally {
                                    withContext(Dispatchers.Main) { st.done.intValue = st.done.intValue + 1 }
                                }
                            }
                        }
                    }.joinAll()
                }

                st.loading = false
                if (st.items.isEmpty() && errors.isNotEmpty()) {
                    st.error = errors.take(2).joinToString("；")
                }
                st.job = null
            }
    }

    fun buildAggregateCard(state: State): RawItem? {
        val sources = state.exactSources.values.distinctBy { "${it.siteKey}::${it.videoId}" }
        if (sources.size < 2) return null
        val cover = state.items.firstOrNull { it.exact } ?: return null
        return RawItem(
            siteKey = "AGG",
            siteName = "聚合 · ${sources.size}源",
            spiderApi = "",
            videoId = "",
            title = cover.title.ifBlank { state.query },
            poster = cover.poster,
            remark = "",
            exact = true,
        )
    }

    private fun buildKey(settings: AppSettings, query: String): String =
        listOf(settings.serverUrl.trim(), settings.serverUsername.trim(), query.trim()).joinToString("|")

    private fun normalizeForMatch(text: String): String {
        val s = text.trim()
        if (s.isBlank()) return ""
        val noZeroWidth = s.replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        return noZeroWidth
            .lowercase()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[·•・.。:：,，/\\\\\\-—_()（）\\[\\]{}<>《》“”\"'‘’!?！？|]+"), "")
            .trim()
    }
}
