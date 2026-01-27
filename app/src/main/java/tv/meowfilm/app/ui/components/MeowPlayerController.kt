package tv.meowfilm.app.ui.components

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class MeowPlayerController(
    context: Context,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private var lastUrl: String = ""
    private var lastHeaders: Map<String, String> = emptyMap()

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

        val dataSourceFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(normalizedHeaders)
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

