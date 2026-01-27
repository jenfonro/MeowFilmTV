package tv.meowfilm.app.data

import org.json.JSONObject

data class CompiledMagicRule(
    val regex: Regex,
    val replace: String?,
)

object MagicRules {
    fun compileReplaceRules(rawRules: List<String>): List<CompiledMagicRule> =
        rawRules.mapNotNull { compileReplaceRule(it) }

    fun compileCleanRegexRules(rawRules: List<String>): List<Regex> =
        rawRules.mapNotNull { compileRegexRule(it) }

    fun applyReplaceRules(text: String, rules: List<CompiledMagicRule>): String {
        var out = text
        rules.forEach { r ->
            try {
                out =
                    if (r.replace != null) {
                        out.replace(r.regex, r.replace)
                    } else {
                        out
                    }
            } catch (_: Throwable) {
            }
        }
        return out
    }

    fun cleanText(text: String, cleanRules: List<Regex>): String {
        var out = text.trim()
        if (out.isEmpty()) return out
        cleanRules.forEach { re ->
            try {
                out = out.replace(re, "")
            } catch (_: Throwable) {
            }
        }
        return out.replace(Regex("\\s+"), " ").trim()
    }

    private fun compileReplaceRule(ruleText: String): CompiledMagicRule? {
        val raw = ruleText.trim()
        if (raw.isEmpty()) return null

        // JSON rule strings like:
        // {"pattern":"...","replace":"...","flags":"i"}
        if (raw.startsWith("{") && raw.endsWith("}")) {
            runCatching {
                val obj = JSONObject(raw)
                val pattern = obj.optString("pattern").orEmpty()
                if (pattern.isBlank()) return null
                val flags = obj.optString("flags").orEmpty()
                val replaceRaw = obj.optString("replace").orEmpty()
                val re = compileRegex(pattern, flags)
                val replace = if (replaceRaw.isNotBlank()) normalizeReplace(replaceRaw) else null
                return CompiledMagicRule(regex = re, replace = replace)
            }.getOrNull()?.let { return it }
        }

        // Literal regex: /pattern/flags
        if (raw.startsWith("/") && raw.lastIndexOf('/') > 0) {
            val last = raw.lastIndexOf('/')
            val pattern = raw.substring(1, last)
            val flags = raw.substring(last + 1)
            return runCatching { CompiledMagicRule(regex = compileRegex(pattern, flags), replace = null) }.getOrNull()
        }

        // Plain pattern (case-insensitive default)
        return runCatching { CompiledMagicRule(regex = compileRegex(raw, "i"), replace = null) }.getOrNull()
    }

    private fun compileRegexRule(ruleText: String): Regex? {
        val raw = ruleText.trim()
        if (raw.isEmpty()) return null
        if (raw.startsWith("/") && raw.lastIndexOf('/') > 0) {
            val last = raw.lastIndexOf('/')
            val pattern = raw.substring(1, last)
            val flags = raw.substring(last + 1)
            return runCatching { compileRegex(pattern, flags) }.getOrNull()
        }
        return runCatching { compileRegex(raw, "i") }.getOrNull()
    }

    private fun compileRegex(pattern: String, flags: String): Regex {
        val normalized = normalizeRegexText(pattern)
        val opts = mutableSetOf<RegexOption>()
        val f = flags.lowercase()
        if ('i' in f) opts += RegexOption.IGNORE_CASE
        if ('m' in f) opts += RegexOption.MULTILINE
        // Kotlin Regex supports DOT_MATCHES_ALL, map from "s".
        if ('s' in f) opts += RegexOption.DOT_MATCHES_ALL
        return Regex(normalized, opts)
    }

    // Users often paste patterns that contain doubled backslashes like `\\d` (from JSON/JS literals).
    private fun normalizeRegexText(text: String): String {
        val raw = text
        if (raw.isEmpty()) return raw
        return raw.replace(Regex("""\\\\(?=[dDsSwWbB.()[\]{}+*?^$|\\\-_/])"""), """\""")
    }

    // Support python-style backrefs like "\1" by converting to Kotlin "$1"
    private fun normalizeReplace(replaceRaw: String): String =
        replaceRaw.replace(Regex("""\\(\d+)""")) { m -> "\$" + (m.groupValues.getOrNull(1) ?: "") }
}

