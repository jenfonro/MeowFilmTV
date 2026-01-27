package tv.meowfilm.app.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object HttpClient {
    data class Response(
        val code: Int,
        val headers: Map<String, List<String>>,
        val body: ByteArray,
    ) {
        fun header(name: String): String {
            val n = name.trim()
            if (n.isEmpty()) return ""
            val values = headers.entries.firstOrNull { it.key.equals(n, ignoreCase = true) }?.value ?: return ""
            return values.firstOrNull().orEmpty()
        }

        fun headers(name: String): List<String> {
            val n = name.trim()
            if (n.isEmpty()) return emptyList()
            return headers.entries.firstOrNull { it.key.equals(n, ignoreCase = true) }?.value ?: emptyList()
        }
    }

    fun getBytes(
        url: String,
        connectTimeoutMs: Int = 12_000,
        readTimeoutMs: Int = 15_000,
        userAgent: String = "MeowFilmTV",
        headers: Map<String, String> = emptyMap(),
    ): ByteArray {
        return request(
            method = "GET",
            url = url,
            body = null,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            userAgent = userAgent,
            headers = headers,
        ).body
    }

    fun postJson(
        url: String,
        jsonBody: String,
        connectTimeoutMs: Int = 12_000,
        readTimeoutMs: Int = 15_000,
        userAgent: String = "MeowFilmTV",
        headers: Map<String, String> = emptyMap(),
    ): Response {
        val extra =
            headers.toMutableMap().apply {
                putIfAbsent("Content-Type", "application/json")
                putIfAbsent("Accept", "application/json")
            }
        return request(
            method = "POST",
            url = url,
            body = jsonBody.toByteArray(Charsets.UTF_8),
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            userAgent = userAgent,
            headers = extra,
        )
    }

    fun get(
        url: String,
        connectTimeoutMs: Int = 12_000,
        readTimeoutMs: Int = 15_000,
        userAgent: String = "MeowFilmTV",
        headers: Map<String, String> = emptyMap(),
    ): Response =
        request(
            method = "GET",
            url = url,
            body = null,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            userAgent = userAgent,
            headers = headers,
        )

    private fun request(
        method: String,
        url: String,
        body: ByteArray?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        userAgent: String,
        headers: Map<String, String>,
    ): Response {
        val conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = method
                setRequestProperty("User-Agent", userAgent)
                headers.forEach { (k, v) ->
                    if (k.isNotBlank() && v.isNotBlank()) setRequestProperty(k, v)
                }
                if (body != null) {
                    doOutput = true
                }
            }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream =
                if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream ?: conn.inputStream
                }
            val bytes =
                stream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            val headerFields = conn.headerFields?.filterKeys { it != null } ?: emptyMap()
            return Response(code = code, headers = headerFields, body = bytes)
        } finally {
            conn.disconnect()
        }
    }
}
