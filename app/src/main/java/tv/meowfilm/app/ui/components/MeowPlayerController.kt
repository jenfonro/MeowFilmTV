package tv.meowfilm.app.ui.components

import android.content.Context
import android.view.ViewGroup
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
import xyz.doikki.videoplayer.ijk.IjkPlayer
import xyz.doikki.videoplayer.ijk.IjkPlayerFactory
import xyz.doikki.videoplayer.player.VideoView

class MeowPlayerController(
    context: Context,
) {
    private val appContext = context.applicationContext

    enum class Engine {
        MEDIA3,
        IJK_SOFT,
    }

    var engine by mutableStateOf(Engine.IJK_SOFT)
        private set

    var stateText by mutableStateOf("idle")
        private set

    var lastError by mutableStateOf("")
        private set

    private var lastUrl: String = ""
    private var lastHeaders: Map<String, String> = emptyMap()
    private var lastAppliedEngine: Engine? = null
    private var retriedFallback: Boolean = false

    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    private val media3Player: ExoPlayer = buildMedia3Player()
    private var ijkView: VideoView<IjkPlayer>? = null

    init {
        attachMedia3Listener(media3Player)
    }

    fun media3(): ExoPlayer = media3Player

    fun ijk(): VideoView<IjkPlayer> {
        val existing = ijkView
        if (existing != null) return existing
        val view =
            VideoView<IjkPlayer>(appContext).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setPlayerFactory(IjkPlayerFactory.create())
                setEnableAudioFocus(true)
                setOnStateChangeListener(
                    object : VideoView.OnStateChangeListener {
                        override fun onPlayerStateChanged(playerState: Int) = Unit

                        override fun onPlayStateChanged(playState: Int) {
                            stateText =
                                when (playState) {
                                    VideoView.STATE_IDLE -> "idle"
                                    VideoView.STATE_PREPARING -> "loading"
                                    VideoView.STATE_PREPARED -> "loading"
                                    VideoView.STATE_BUFFERING -> "buffering"
                                    VideoView.STATE_BUFFERED -> "ready"
                                    VideoView.STATE_PLAYING -> "ready"
                                    VideoView.STATE_PAUSED -> "paused"
                                    VideoView.STATE_PLAYBACK_COMPLETED -> "ended"
                                    VideoView.STATE_ERROR -> {
                                        lastError = "播放失败"
                                        if (!retriedFallback && engine == Engine.IJK_SOFT && lastUrl.isNotBlank()) {
                                            retriedFallback = true
                                            switchTo(Engine.MEDIA3)
                                            setSource(url = lastUrl, headers = lastHeaders, force = true)
                                        }
                                        "error"
                                    }
                                    else -> stateText
                                }
                        }
                    },
                )
            }
        ijkView = view
        return view
    }

    private fun buildMedia3Player(): ExoPlayer {
        val renderersFactory =
            DefaultRenderersFactory(appContext)
                .setEnableDecoderFallback(true)
                // Keep ON so if a user adds a local extension later it can be used.
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        return ExoPlayer.Builder(appContext).setRenderersFactory(renderersFactory).build()
    }

    private fun attachMedia3Listener(p: ExoPlayer) {
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
                        val cause = c ?: break
                        val cm = cause.message.orEmpty().trim()
                        parts += buildString {
                            append(cause::class.java.simpleName)
                            if (cm.isNotBlank()) append(": ").append(cm)
                        }
                        c = cause.cause
                        depth++
                    }
                    if (parts.isEmpty()) parts += error.errorCodeName
                    val raw = parts.distinct().joinToString(" | ")

                    val looksLikeHevcCodecFailure =
                        raw.contains("MediaCodecVideoRenderer", ignoreCase = true) &&
                            (raw.contains("video/hevc", ignoreCase = true) || raw.contains("hvc1", ignoreCase = true))

                    lastError =
                        if (looksLikeHevcCodecFailure) {
                            "$raw | 提示：当前视频为 HEVC/H.265，可能超出硬解能力（清晰度/10bit/等级）。"
                        } else {
                            raw
                        }
                    stateText = "error"

                    if (looksLikeHevcCodecFailure && engine == Engine.MEDIA3) {
                        // Fall back to IJK software decoder if available.
                        switchTo(Engine.IJK_SOFT)
                        setSource(url = lastUrl, headers = lastHeaders, force = true)
                    }
                }
            },
        )
    }

    fun setSource(
        url: String,
        headers: Map<String, String>,
        force: Boolean = false,
    ) {
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

        if (!force && u == lastUrl && normalizedHeaders == lastHeaders && lastAppliedEngine == engine) return
        lastUrl = u
        lastHeaders = normalizedHeaders
        lastAppliedEngine = engine
        retriedFallback = false

        lastError = ""
        stateText = "loading"

        when (engine) {
            Engine.MEDIA3 -> {
                val dataSourceFactory: DataSource.Factory =
                    OkHttpDataSource.Factory(okHttpClient)
                        .setDefaultRequestProperties(normalizedHeaders)

                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
                val mediaSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(u))
                media3Player.apply {
                    setMediaSource(mediaSource)
                    prepare()
                    playWhenReady = true
                    play()
                }
            }

            Engine.IJK_SOFT -> {
                // IjkPlayer mutates the headers map (removes User-Agent), so pass a copy.
                val headersCopy = normalizedHeaders.toMutableMap()
                ijk().apply {
                    setUrl(u, headersCopy)
                    val playState = currentPlayState
                    if (playState == VideoView.STATE_IDLE || playState == VideoView.STATE_START_ABORT) {
                        start()
                    } else {
                        replay(true)
                    }
                }
            }
        }
    }

    fun switchTo(next: Engine) {
        if (engine == next) return
        when (engine) {
            Engine.MEDIA3 -> runCatching { media3Player.pause(); media3Player.stop(); media3Player.clearMediaItems() }
            Engine.IJK_SOFT -> runCatching { ijkView?.release() }
        }
        lastAppliedEngine = null
        retriedFallback = false
        engine = next
        lastError = ""
        stateText = "idle"
    }

    fun stop() {
        lastError = ""
        stateText = "idle"
        lastUrl = ""
        lastHeaders = emptyMap()
        lastAppliedEngine = null
        retriedFallback = false
        runCatching {
            when (engine) {
                Engine.MEDIA3 -> {
                    media3Player.playWhenReady = false
                    media3Player.pause()
                    media3Player.stop()
                    media3Player.clearMediaItems()
                }

                Engine.IJK_SOFT -> {
                    ijkView?.release()
                }
            }
        }
    }

    fun release() {
        stop()
        runCatching { media3Player.release() }
        runCatching { ijkView?.release() }
        ijkView = null
    }
}
