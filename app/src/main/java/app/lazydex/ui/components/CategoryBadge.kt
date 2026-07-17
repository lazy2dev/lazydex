package app.lazydex.ui.components

   import androidx.compose.foundation.border
   import androidx.compose.foundation.layout.Row
   import androidx.compose.foundation.layout.Spacer
   import androidx.compose.foundation.layout.padding
   import androidx.compose.foundation.layout.size
   import androidx.compose.foundation.layout.width
   import androidx.compose.foundation.shape.RoundedCornerShape
   import androidx.compose.material.icons.Icons
   import androidx.compose.material.icons.filled.Book
   import androidx.compose.material.icons.filled.Gamepad
   import androidx.compose.material.icons.filled.Movie
   import androidx.compose.material.icons.filled.Tv
   import androidx.compose.material3.Icon
   import androidx.compose.material3.Text
   import androidx.compose.runtime.Composable
   import androidx.compose.ui.Alignment
   import androidx.compose.ui.Modifier
   import androidx.compose.ui.unit.dp
   import androidx.compose.ui.unit.sp
   import app.lazydex.domain.model.MediaCategory
   import app.lazydex.ui.theme.CategoryAnimeColor
   import app.lazydex.ui.theme.CategoryGameColor
   import app.lazydex.ui.theme.CategoryMangaColor
   import app.lazydex.ui.theme.CategoryMovieColor
   import app.lazydex.ui.theme.CategoryNovelColor
   import app.lazydex.ui.theme.CategoryTvColor

   @Composable
   fun CategoryBadge(category: MediaCategory, modifier: Modifier = Modifier) {
       val (icon, color) = when (category) {
           MediaCategory.NOVEL -> Icons.Default.Book to CategoryNovelColor
           MediaCategory.MANGA -> Icons.Default.Book to CategoryMangaColor
           MediaCategory.ANIME -> Icons.Default.Tv to CategoryAnimeColor
           MediaCategory.GAME -> Icons.Default.Gamepad to CategoryGameColor
           MediaCategory.MOVIE -> Icons.Default.Movie to CategoryMovieColor
           MediaCategory.TV -> Icons.Default.Tv to CategoryTvColor
       }

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
               text = category.displayName,
               color = color,
               fontSize = 10.sp
           )
       }
   }
