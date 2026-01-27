package tv.meowfilm.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

data class DoubanItem(
    val id: String,
    val title: String,
    val poster: String,
    val rate: String,
    val year: String,
    val isBangumi: Boolean,
)

object DoubanClient {
    fun fetchHomeRow(
        type: String,
        settings: AppSettings,
        limit: Int = 20,
    ): List<DoubanItem> {
        return when (type) {
            "movie" ->
                fetchDoubanRecentHot(
                    kind = "movie",
                    category = "热门",
                    hotType = "全部",
                    start = 0,
                    limit = limit,
                    settings = settings,
                )

            "tv" ->
                fetchDoubanRecentHot(
                    kind = "tv",
                    category = "tv",
                    hotType = "tv",
                    start = 0,
                    limit = limit,
                    settings = settings,
                )

            "show" ->
                fetchDoubanRecentHot(
                    kind = "tv",
                    category = "show",
                    hotType = "show",
                    start = 0,
                    limit = limit,
                    settings = settings,
                )

            "anime" -> fetchBangumiToday(limit = limit)
            else -> emptyList()
        }
    }

    private fun fetchDoubanRecentHot(
        kind: String,
        category: String,
        hotType: String,
        start: Int,
        limit: Int,
        settings: AppSettings,
    ): List<DoubanItem> {
        val (m, proxyBase) = getDataApiBase(settings)
        val target =
            "$m/rexxar/api/v2/subject/recent_hot/$kind?start=$start&limit=$limit&category=$category&type=$hotType"
        val url = if (proxyBase.isNotBlank()) toProxiedUrl(target, proxyBase) else target
        val raw = String(HttpClient.getBytes(url), Charsets.UTF_8)
        val json = JSONObject(raw)
        val items = json.optJSONArray("items") ?: JSONArray()
        return (0 until items.length())
            .mapNotNull { idx ->
                val item = items.optJSONObject(idx) ?: return@mapNotNull null
                val id = item.optString("id", "")
                val title = item.optString("title", "")
                val pic = item.optJSONObject("pic")
                val poster = pic?.optString("normal").orEmpty().ifBlank { pic?.optString("large").orEmpty() }
                val rating = item.optJSONObject("rating")?.optDouble("value", 0.0) ?: 0.0
                val rate = if (rating > 0.0) String.format("%.1f", rating) else ""
                val year = extractYear(item.optString("card_subtitle", ""))
                DoubanItem(
                    id = id,
                    title = title,
                    poster = processPosterUrl(poster, settings),
                    rate = rate,
                    year = year,
                    isBangumi = false,
                )
            }
    }

    private fun fetchBangumiToday(limit: Int): List<DoubanItem> {
        val raw = String(HttpClient.getBytes("https://api.bgm.tv/calendar"), Charsets.UTF_8)
        val arr = JSONArray(raw)
        val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val current = weekdays[java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1]
        val matched =
            (0 until arr.length())
                .mapNotNull { arr.optJSONObject(it) }
                .firstOrNull { it.optJSONObject("weekday")?.optString("en") == current }
                ?: return emptyList()
        val items = matched.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<DoubanItem>(minOf(limit, items.length()))
        for (i in 0 until items.length()) {
            if (out.size >= limit) break
            val it = items.optJSONObject(i) ?: continue
            val images = it.optJSONObject("images") ?: continue
            val title = it.optString("name_cn").ifBlank { it.optString("name") }
            if (title.isBlank()) continue
            val poster =
                images.optString("large").ifBlank {
                    images.optString("common").ifBlank {
                        images.optString("medium").ifBlank {
                            images.optString("small").ifBlank { images.optString("grid") }
                        }
                    }
                }
            val rating = it.optJSONObject("rating")?.optDouble("score", 0.0) ?: 0.0
            val rate = if (rating > 0.0) String.format("%.1f", rating) else ""
            val year = it.optString("air_date").split("-").getOrNull(0).orEmpty()
            out.add(
                DoubanItem(
                    id = it.optString("id", ""),
                    title = title,
                    poster = poster,
                    rate = rate,
                    year = year,
                    isBangumi = true,
                ),
            )
        }
        return out
    }

    private fun getDataApiBase(settings: AppSettings): Pair<String, String> {
        val p = settings.doubanDataProxy.trim()
        if (p == "cdn-tx" || p == "cmliussss-cdn-tencent") return "https://m.douban.cmliussss.net" to ""
        if (p == "cdn-ali" || p == "cmliussss-cdn-ali") return "https://m.douban.cmliussss.com" to ""
        if (p == "cors" || p == "cors-proxy-zwei") return "https://m.douban.com" to "https://ciao-cors.is-an.org/"
        if (p == "cors-anywhere") return "https://m.douban.com" to "https://cors-anywhere.com/"
        if (p == "custom") return "https://m.douban.com" to settings.doubanDataCustom
        return "https://m.douban.com" to ""
    }

    private fun normalizeProxyBase(base: String): String {
        val raw = base.trim()
        if (raw.isBlank()) return ""
        if (Regex("[?&=]$").containsMatchIn(raw)) return raw
        return if (raw.endsWith("/")) raw else "$raw/"
    }

    private fun toProxiedUrl(targetUrl: String, proxyBase: String): String {
        val normalized = normalizeProxyBase(proxyBase)
        if (normalized.isBlank()) return targetUrl
        if (normalized.contains("cors-anywhere.com/")) return normalized + targetUrl
        return normalized + URLEncoder.encode(targetUrl, "UTF-8")
    }

    private fun extractYear(text: String): String =
        Regex("(\\d{4})").find(text)?.groupValues?.getOrNull(1).orEmpty()

    private fun normalizeImageUrl(url: String): String {
        val raw = url.trim()
        if (raw.isBlank()) return ""
        if (raw.startsWith("//")) return "https:$raw"
        if (raw.startsWith("http://")) return "https://" + raw.removePrefix("http://")
        return raw
    }

    private fun isAllowedDoubanImageHost(host: String): Boolean {
        val h = host.trim().lowercase()
        if (h.isBlank()) return false
        if (Regex("^img\\d+\\.doubanio\\.com$").matches(h)) return true
        if (h == "img3.doubanio.com") return true
        if (h == "img.doubanio.cmliussss.net") return true
        if (h == "img.doubanio.cmliussss.com") return true
        return false
    }

    private fun swapDoubanImageHost(urlStr: String, nextHost: String): String {
        val original = normalizeImageUrl(urlStr)
        val target = nextHost.trim()
        if (original.isBlank() || target.isBlank()) return original
        return try {
            val u = URL(original)
            if (!isAllowedDoubanImageHost(u.host)) return original
            URL("https", target, u.file).toString()
        } catch (_e: Throwable) {
            original.replace(
                Regex("(img\\d+\\.doubanio\\.com|img3\\.doubanio\\.com|img\\.doubanio\\.cmliussss\\.(net|com))", RegexOption.IGNORE_CASE),
                target,
            )
        }
    }

    fun processPosterUrl(posterUrl: String, settings: AppSettings): String {
        val original = normalizeImageUrl(posterUrl)
        if (original.isBlank()) return ""

        val host =
            try {
                URL(original).host.orEmpty()
            } catch (_e: Throwable) {
                ""
            }
        if (!isAllowedDoubanImageHost(host) && !original.contains("doubanio")) return original

        val mode = settings.doubanImgProxy.trim().ifBlank { "direct-browser" }

        if (mode == "server-proxy") {
            val base = settings.serverUrl.trim().removeSuffix("/")
            return if (base.isBlank()) original else "$base/api/douban/image?url=${URLEncoder.encode(original, "UTF-8")}"
        }
        if (mode == "custom") {
            val base = normalizeProxyBase(settings.doubanImgCustom)
            return if (base.isBlank()) original else base + URLEncoder.encode(original, "UTF-8")
        }

        return when (mode) {
            "douban-cdn-ali", "img3" -> swapDoubanImageHost(original, "img3.doubanio.com")
            "cdn-tx", "cmliussss-cdn-tencent" -> swapDoubanImageHost(original, "img.doubanio.cmliussss.net")
            "cdn-ali", "cmliussss-cdn-ali" -> swapDoubanImageHost(original, "img.doubanio.cmliussss.com")
            else -> original
        }
    }

    fun fetchHotWords(
        settings: AppSettings,
        limit: Int = 20,
    ): List<String> {
        val headers =
            mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Referer" to "https://m.douban.com/",
            )
        val endpoints =
            buildDataEndpoints(
                settings = settings,
                path = "/rexxar/api/v2/search/hot_queries",
            )
        endpoints.forEach { url ->
            val list =
                runCatching {
                    val raw = String(HttpClient.getBytes(url, headers = headers), Charsets.UTF_8)
                    val json = JSONObject(raw)
                    extractWords(json)
                }.getOrElse { emptyList() }
            if (list.isNotEmpty()) return list.distinct().take(limit)
        }
        return emptyList()
    }

    fun fetchSuggestWords(
        query: String,
        settings: AppSettings,
        limit: Int = 12,
    ): List<String> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val headers =
            mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Referer" to "https://m.douban.com/",
            )
        val encoded = URLEncoder.encode(q, "UTF-8")
        val endpoints =
            buildDataEndpoints(
                settings = settings,
                path = "/rexxar/api/v2/search/suggest?q=$encoded",
            )
        endpoints.forEach { url ->
            val list =
                runCatching {
                    val raw = String(HttpClient.getBytes(url, headers = headers), Charsets.UTF_8)
                    val json = JSONObject(raw)
                    extractWords(json)
                }.getOrElse { emptyList() }
            if (list.isNotEmpty()) return list.distinct().take(limit)
        }

        // Fallback: web suggest endpoint
        val fallbackUrl = "https://www.douban.com/j/search_suggest?q=$encoded"
        return runCatching {
            val raw =
                String(
                    HttpClient.getBytes(
                        fallbackUrl,
                        headers =
                            mapOf(
                                "Accept" to "application/json, text/plain, */*",
                                "Referer" to "https://www.douban.com/",
                            ),
                    ),
                    Charsets.UTF_8,
                )
            val json = JSONObject(raw)
            extractWords(json)
        }.getOrElse { emptyList() }.distinct().take(limit)
    }

    private fun buildDataEndpoints(settings: AppSettings, path: String): List<String> {
        val p = path.trim()
        if (p.isBlank() || !p.startsWith("/")) return emptyList()
        val (m, proxyBase) = getDataApiBase(settings)

        val out = ArrayList<String>(3)
        val primaryTarget = m.trim().removeSuffix("/") + p
        out.add(if (proxyBase.isNotBlank()) toProxiedUrl(primaryTarget, proxyBase) else primaryTarget)

        // Fallback to official host when using CDN mirrors
        if (!m.contains("m.douban.com")) {
            val fallbackTarget = "https://m.douban.com$p"
            out.add(if (proxyBase.isNotBlank()) toProxiedUrl(fallbackTarget, proxyBase) else fallbackTarget)
        }
        return out.distinct()
    }

    private fun extractWords(json: JSONObject): List<String> {
        val keys = listOf("hot_queries", "words", "items", "suggestions", "data", "queries")
        keys.forEach { k ->
            val arr = json.optJSONArray(k) ?: return@forEach
            val list = extractWords(arr)
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    private fun extractWords(arr: JSONArray): List<String> {
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.opt(i) ?: continue
            when (v) {
                is String -> out.add(v.trim())
                is JSONObject -> {
                    val w =
                        v.optString("text").ifBlank {
                            v.optString("word").ifBlank {
                                v.optString("query").ifBlank { v.optString("title") }
                            }
                        }.trim()
                    if (w.isNotBlank()) out.add(w)
                }
            }
        }
        return out.filter { it.isNotBlank() }
    }
}
