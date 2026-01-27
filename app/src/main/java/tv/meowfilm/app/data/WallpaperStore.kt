package tv.meowfilm.app.data

import android.content.Context
import java.io.File

object WallpaperStore {
    fun wallpaperFile(context: Context): File =
        File(context.filesDir, "wallpaper.jpg")
}

