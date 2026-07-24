package app.lazydex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun CoverImage(
    coverImagePath: String,
    title: String,
    modifier: Modifier = Modifier,
    coverImageUrl: String? = null
) {
    val imageModel: Any? = remember(coverImagePath, coverImageUrl) {
        when {
            coverImagePath.isNotBlank() -> File(coverImagePath)
            !coverImageUrl.isNullOrBlank() -> coverImageUrl
            else -> null
        }
    }

    var hasError by remember(imageModel) { mutableStateOf(imageModel == null) }

    val initials = remember(title) {
        val parts = title.trim().split(Regex("\\s+"))
        val firstChar = parts.firstOrNull()?.firstOrNull()?.toString() ?: ""
        val secondChar = if (parts.size > 1) parts[1].firstOrNull()?.toString() ?: "" else ""
        (firstChar + secondChar).uppercase().ifEmpty { "?" }
    }

    val fallbackGradient = remember {
        Brush.linearGradient(
            colors = listOf(Color(0xFF2C3E50), Color(0xFF000000))
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(fallbackGradient)
    ) {
        if (!hasError && imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = "Cover for $title",
                contentScale = ContentScale.Crop,
                onError = { hasError = true },
                onSuccess = { hasError = false },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = initials,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
