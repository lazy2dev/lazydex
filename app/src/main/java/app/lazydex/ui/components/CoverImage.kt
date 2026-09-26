package app.lazydex.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import app.lazydex.R
import app.lazydex.ui.theme.CoverPlaceholderColor
import app.lazydex.ui.theme.CoverPlaceholderOnBgColor
import coil3.compose.SubcomposeAsyncImage
import java.io.File

@Composable
fun CoverImage(
    coverImagePath: String,
    title: String,
    modifier: Modifier = Modifier,
    coverImageUrl: String? = null,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    val fileExists = remember(coverImagePath) {
        coverImagePath.isNotEmpty() && File(coverImagePath).exists()
    }

    val imageModel: Any? = remember(coverImagePath, coverImageUrl, fileExists) {
        if (fileExists) File(coverImagePath)
        else coverImageUrl?.takeIf { it.isNotBlank() }
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(CoverPlaceholderColor),
        contentAlignment = Alignment.Center
    ) {
        val isSmall = maxWidth < 60.dp || maxHeight < 80.dp
        val iconSize = if (isSmall) 24.dp else 32.dp
        val strokeWidth = if (isSmall) 2.dp else 3.dp

        if (imageModel != null) {
            SubcomposeAsyncImage(
                model = imageModel,
                contentDescription = "Cover for $title",
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CoverPlaceholderOnBgColor,
                            modifier = Modifier.size(iconSize),
                            strokeWidth = strokeWidth,
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            imageVector = ImageVector.vectorResource(R.drawable.cover_error_vector),
                            contentDescription = "Cover for $title",
                            modifier = Modifier.size(iconSize),
                            colorFilter = ColorFilter.tint(CoverPlaceholderOnBgColor)
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                imageVector = ImageVector.vectorResource(R.drawable.cover_error_vector),
                contentDescription = "Cover for $title",
                modifier = Modifier.size(iconSize),
                colorFilter = ColorFilter.tint(CoverPlaceholderOnBgColor)
            )
        }
    }
}
