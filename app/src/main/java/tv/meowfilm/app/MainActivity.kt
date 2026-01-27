package tv.meowfilm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import tv.meowfilm.app.ui.MeowFilmTvApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val ready = remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { ready.value = true }
            if (ready.value) {
                MeowFilmTvApp()
            }
        }
    }
}

