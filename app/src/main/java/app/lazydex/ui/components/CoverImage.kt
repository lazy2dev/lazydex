package app.lazydex.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import app.lazydex.R
import app.lazydex.ui.theme.CoverPlaceholderColor
import app.lazydex.ui.theme.CoverPlaceholderOnBgColor
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File

@Composable
fun CoverImage(
    coverImagePath: String,
    title: String,
    modifier: Modifier = Modifier,
    coverImageUrl: String? = null,
    shape: Shape = RoundedCornerShape(4.dp),
    isObscured: Boolean = false,
) {
    val imageModel: Any? = remember(coverImagePath, coverImageUrl) {
        when {
            coverImagePath.isNotBlank() && File(coverImagePath).exists() -> File(coverImagePath)
            !coverImageUrl.isNullOrBlank() -> coverImageUrl
            else -> null
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(CoverPlaceholderColor),
        contentAlignment = Alignment.Center
    ) {
        if (isObscured) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2B1414)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    imageVector = ImageVector.vectorResource(R.drawable.cover_error_vector),
                    contentDescription = "Adult Content Hidden",
                    colorFilter = ColorFilter.tint(Color(0xFFEF5350)),
                    modifier = Modifier.size(28.dp)
                )
            }
        } else if (imageModel != null) {
            val context = LocalContext.current
            val request = remember(imageModel) {
                ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = "Cover for $title",
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.cover_error_vector),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                imageVector = ImageVector.vectorResource(R.drawable.cover_error_vector),
                contentDescription = "Cover for $title",
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(CoverPlaceholderOnBgColor)
            )
        }
    }
}
