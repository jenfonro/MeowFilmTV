package tv.meowfilm.app.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.net.URI
import okhttp3.OkHttpClient

class MeowPlayerController(
    context: Context,
) {
    private val appContext = context.applicationContext

    // Expose as state so PlayerView updates when we swap the instance (e.g. retry with FFmpeg video renderer).
    var player: ExoPlayer by mutableStateOf(buildPlayer(preferExtension = false))
        private set

    var stateText by mutableStateOf("idle")
        private set

    var lastError by mutableStateOf("")
        private set

    private var lastUrl: String = ""
    private var lastHeaders: Map<String, String> = emptyMap()
    private var retriedWithPreferExtension: Boolean = false

    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    init {
        attachListener(player)
    }

    private fun buildPlayer(preferExtension: Boolean): ExoPlayer {
        val mode =
            if (preferExtension) {
                // Prefer extension (FFmpeg) video renderer when available.
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            } else {
                // Keep hardware decoders preferred; extension is available but not prioritized.
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }

        val renderersFactory =
            DefaultRenderersFactory(appContext)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(mode)

        return ExoPlayer.Builder(appContext).setRenderersFactory(renderersFactory).build()
    }

    private fun attachListener(p: ExoPlayer) {
        p.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    stateText =
                        when (playbackState) {
                            Player.STATE_IDLE -> "idle"
                            Player.STATE_BUFFERING -> "buffering"
                            Player.STATE_READY -> "ready"
                            Player.STATE_ENDED -> "ended"
                            else -> "unknown"
                        }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val parts = mutableListOf<String>()
                    val msg = error.message.orEmpty().trim()
                    if (msg.isNotBlank()) parts += msg
                    var c: Throwable? = error.cause
                    var depth = 0
                    while (c != null && depth < 3) {
                        val cm = c.message.orEmpty().trim()
                        parts += buildString {
                            append(c::class.java.simpleName)
                            if (cm.isNotBlank()) append(": ").append(cm)
                        }
                        c = c.cause
                        depth++
                    }
                    if (parts.isEmpty()) parts += error.errorCodeName
                    val raw = parts.distinct().joinToString(" | ")

                    val looksLikeHevcCodecFailure =
                        raw.contains("MediaCodecVideoRenderer", ignoreCase = true) &&
                            (raw.contains("video/hevc", ignoreCase = true) || raw.contains("hvc1", ignoreCase = true))

                    // If we are failing on HEVC via MediaCodec, retry once with extension preferred (FFmpeg video renderer).
                    if (looksLikeHevcCodecFailure && !retriedWithPreferExtension && lastUrl.isNotBlank()) {
                        retriedWithPreferExtension = true
                        swapPlayer(preferExtension = true)
                        // Force reload with the same source.
                        setSource(url = lastUrl, headers = lastHeaders, force = true)
                        return
                    }

                    lastError =
                        if (looksLikeHevcCodecFailure) {
                            "$raw | 提示：当前视频为 HEVC/H.265，可能超出硬解能力（清晰度/10bit/等级）。"
                        } else {
                            raw
                        }
                    stateText = "error"
                }
            },
        )
    }

    private fun swapPlayer(preferExtension: Boolean) {
        val old = player
        val next = buildPlayer(preferExtension)
        attachListener(next)
        player = next
        runCatching { old.release() }
    }

    fun setSource(url: String, headers: Map<String, String>, force: Boolean = false) {
        val u = url.trim()
        if (u.isBlank()) return
        val normalizedHeaders =
            headers
                .mapNotNull { (k, v) ->
                    val kk = k.trim()
                    val vv = v.replace("\r", " ").replace("\n", " ").trim()
                    if (kk.isBlank() || vv.isBlank()) null else kk to vv
                }
                .toMap()
                .toMutableMap()
                .apply {
                    putIfAbsent("User-Agent", "MeowFilmTV")
                    // Some providers (e.g., Quark) require Origin when Referer is present.
                    val ref = entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value.orEmpty().trim()
                    if (ref.isNotBlank() && keys.none { it.equals("Origin", ignoreCase = true) }) {
                        runCatching {
                            val uri = URI(ref)
                            val scheme = uri.scheme ?: return@runCatching
                            val host = uri.host ?: return@runCatching
                            val port = uri.port
                            val origin =
                                if (port > 0 && port != 80 && port != 443) "$scheme://$host:$port" else "$scheme://$host"
                            if (origin.isNotBlank()) {
                                put("Origin", origin)
                            }
                        }
                    }
                }

        if (!force && u == lastUrl && normalizedHeaders == lastHeaders) return
        lastUrl = u
        lastHeaders = normalizedHeaders

        lastError = ""
        stateText = "loading"

        val dataSourceFactory: DataSource.Factory =
            OkHttpDataSource.Factory(okHttpClient)
                .setDefaultRequestProperties(normalizedHeaders)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(u))
        player.apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
            play()
        }
    }

    fun release() {
        runCatching { player.release() }
    }
}
