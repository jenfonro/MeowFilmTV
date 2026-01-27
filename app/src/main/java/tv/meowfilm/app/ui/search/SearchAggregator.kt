package tv.meowfilm.app.ui.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import tv.meowfilm.app.data.CatPawOpenClient
import tv.meowfilm.app.data.MagicRules
import tv.meowfilm.app.data.MeowFilmSite
import tv.meowfilm.app.ui.VideoSourcePayload
import tv.meowfilm.app.ui.meowfilm.MeowFilmStore
import tv.meowfilm.app.data.AppSettings

data class AggregatedResult(
    val title: String,
    val poster: String,
    val remark: String,
    val sources: List<VideoSourcePayload>,
)

object SearchAggregator {
    suspend fun aggregate(
        settings: AppSettings,
        meowFilmStore: MeowFilmStore,
        keyword: String,
    ): List<AggregatedResult> {
        val q = keyword.trim()
        if (q.isBlank()) return emptyList()

        val boot = meowFilmStore.bootstrap ?: throw IllegalStateException("未登录或未加载配置")
        val catBase = meowFilmStore.catApiBase().trim()
        val tvUser = meowFilmStore.tvUser().trim()
        val sites = meowFilmStore.sites.filter { it.enabled && it.search }
        if (catBase.isBlank() || tvUser.isBlank() || sites.isEmpty()) throw IllegalStateException("站点未配置或不可用")

        val order = boot.settings.searchSiteOrder
        val orderMap = order.withIndex().associate { it.value to it.index }
        val coverSite = boot.settings.searchCoverSite.trim()
        val compiledAggRules = MagicRules.compileReplaceRules(boot.settings.magicAggregateRegexRules)
        val concurrency = boot.settings.searchThreadCount.coerceIn(1, 20)

        val errors = mutableListOf<String>()
        val items =
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val chunks = sites.chunked(maxOf(1, (sites.size + concurrency - 1) / concurrency))
                    chunks.map { chunk ->
                        async {
                            val out = mutableListOf<tv.meowfilm.app.data.CatSearchItem>()
                            chunk.forEach { site ->
                                runCatching {
                                    CatPawOpenClient.search(
                                        apiBase = catBase,
                                        tvUser = tvUser,
                                        site = site,
                                        keyword = q,
                                        page = 1,
                                    )
                                }.onSuccess { out.addAll(it) }
                                    .onFailure { e ->
                                        synchronized(errors) {
                                            errors.add("${site.name.ifBlank { site.key }}: ${e.message ?: "error"}")
                                        }
                                    }
                            }
                            out
                        }
                    }.awaitAll().flatten()
                }
            }
        if (items.isEmpty() && errors.isNotEmpty()) {
            throw IllegalStateException(errors.take(2).joinToString("；"))
        }

        fun normalizeForKey(s: String): String {
            val base = MagicRules.applyReplaceRules(s, compiledAggRules).trim()
            if (base.isBlank()) return ""
            val noZeroWidth = base.replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            return noZeroWidth
                .lowercase()
                .replace(Regex("\\s+"), "")
                .replace(Regex("[·•・.。:：,，/\\\\\\-—_()（）\\[\\]{}<>《》“”\"'‘’!?！？|]+"), "")
                .trim()
        }

        val grouped = LinkedHashMap<String, MutableList<tv.meowfilm.app.data.CatSearchItem>>()
        items.forEach { it ->
            val k = normalizeForKey(it.title)
            if (k.isBlank()) return@forEach
            val bucket = grouped.getOrPut(k) { mutableListOf() }
            bucket.add(it)
        }

        fun pickPrimary(list: List<tv.meowfilm.app.data.CatSearchItem>): tv.meowfilm.app.data.CatSearchItem {
            if (list.isEmpty()) throw IllegalStateException("empty")
            if (coverSite.isNotBlank()) {
                list.firstOrNull { it.siteKey == coverSite }?.let { return it }
            }
            return list.minByOrNull { orderMap[it.siteKey] ?: 999999 } ?: list.first()
        }

        return grouped.values
            .mapNotNull { bucket ->
                if (bucket.isEmpty()) return@mapNotNull null
                val primary = pickPrimary(bucket)
                val srcs =
                    bucket
                        .distinctBy { "${it.siteKey}::${it.videoId}" }
                        .map {
                            VideoSourcePayload(
                                siteKey = it.siteKey,
                                siteName = it.siteName,
                                spiderApi = it.spiderApi,
                                videoId = it.videoId,
                            )
                        }
                AggregatedResult(
                    title = primary.title,
                    poster = primary.poster,
                    remark = primary.remark,
                    sources = srcs,
                )
            }
            .sortedWith(
                compareBy<AggregatedResult> { orderMap[it.sources.firstOrNull()?.siteKey ?: ""] ?: 999999 }
                    .thenBy { it.title },
            )
    }
}
