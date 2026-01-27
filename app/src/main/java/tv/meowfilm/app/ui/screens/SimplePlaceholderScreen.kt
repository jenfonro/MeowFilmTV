package tv.meowfilm.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tv.meowfilm.app.ui.components.MeowFilmBackground
import tv.meowfilm.app.ui.components.MeowFilmTopBar
import tv.meowfilm.app.ui.components.TopBarAction

@Composable
fun SimplePlaceholderScreen(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        MeowFilmBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, start = 26.dp, end = 26.dp),
        ) {
            MeowFilmTopBar(
                title = title,
                subtitle = "",
            ) {
                TopBarAction(
                    icon = Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF141B2A),
                                Color(0xFF0B0F14),
                            ),
                        ),
                        RoundedCornerShape(26.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
            }
        }
    }
}
