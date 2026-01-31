package tv.meowfilm.app.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class MeowPlayerController(
    context: Context,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    var stateText by mutableStateOf("idle")
        private set

    var lastError by mutableStateOf("")
        private set

    private var lastUrl: String = ""
    private var lastHeaders: Map<String, String> = emptyMap()

    init {
        player.addListener(
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
                    lastError = error.message.orEmpty().ifBlank { error.errorCodeName }
                    stateText = "error"
                }
            },
        )
    }

    fun setSource(url: String, headers: Map<String, String>) {
        val u = url.trim()
        if (u.isBlank()) return
        val normalizedHeaders =
            headers
                .mapNotNull { (k, v) ->
                    val kk = k.trim()
                    val vv = v.trim()
                    if (kk.isBlank() || vv.isBlank()) null else kk to vv
                }
                .toMap()
                .toMutableMap()
                .apply {
                    putIfAbsent("User-Agent", "MeowFilmTV")
                }

        if (u == lastUrl && normalizedHeaders == lastHeaders) return
        lastUrl = u
        lastHeaders = normalizedHeaders

        lastError = ""
        stateText = "loading"

        val dataSourceFactory =
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(normalizedHeaders)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(u))
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
        player.play()
    }

    fun release() {
        player.release()
    }
}
