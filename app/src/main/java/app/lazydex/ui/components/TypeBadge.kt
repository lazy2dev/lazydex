package app.lazydex.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.MediaType
import app.lazydex.ui.theme.TypeAnimeColor
import app.lazydex.ui.theme.TypeGameColor
import app.lazydex.ui.theme.TypeMangaColor
import app.lazydex.ui.theme.TypeMovieColor
import app.lazydex.ui.theme.TypeNovelColor
import app.lazydex.ui.theme.TypeTvColor

fun MediaType.icon(): ImageVector = when (this) {
    MediaType.NOVEL -> Icons.Default.AutoStories
    MediaType.MANGA -> Icons.AutoMirrored.Filled.MenuBook
    MediaType.ANIME -> Icons.Default.SmartDisplay
    MediaType.GAME -> Icons.Default.SportsEsports
    MediaType.MOVIE -> Icons.Default.Movie
    MediaType.TV -> Icons.Default.Tv
}

fun MediaType.color(): Color = when (this) {
    MediaType.NOVEL -> TypeNovelColor
    MediaType.MANGA -> TypeMangaColor
    MediaType.ANIME -> TypeAnimeColor
    MediaType.GAME -> TypeGameColor
    MediaType.MOVIE -> TypeMovieColor
    MediaType.TV -> TypeTvColor
}

@Composable
fun TypeBadge(mediaType: MediaType, modifier: Modifier = Modifier) {
    val icon = mediaType.icon()
    val color = mediaType.color()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = mediaType.displayName,
            color = color,
            fontSize = 10.sp
        )
    }
}
