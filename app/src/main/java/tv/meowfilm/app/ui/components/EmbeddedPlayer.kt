package tv.meowfilm.app.ui.components

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@Composable
fun EmbeddedPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            this.playWhenReady = playWhenReady
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(url, headers) {
        val u = url.trim()
        if (u.isBlank()) return@LaunchedEffect

        val props = HashMap<String, String>()
        headers.forEach { (k, v) ->
            val kk = k.trim()
            val vv = v.trim()
            if (kk.isNotBlank() && vv.isNotBlank()) props[kk] = vv
        }
        if (!props.containsKey("User-Agent")) {
            props["User-Agent"] = "MeowFilmTV"
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(props)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(u))
        player.setMediaSource(mediaSource)
        player.prepare()
        if (playWhenReady) player.play()
    }

    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(it).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                useController = true
                this.player = player
            }
        },
        update = { it.player = player },
    )
}

