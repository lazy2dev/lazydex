package app.lazydex.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.ui.theme.Rating1
import app.lazydex.ui.theme.Rating2
import app.lazydex.ui.theme.Rating3
import app.lazydex.ui.theme.Rating4
import app.lazydex.ui.theme.Rating5

@Composable
fun StarRating(
    rating: Double?,
    modifier: Modifier = Modifier,
    isEditable: Boolean = false,
    onRatingChanged: (Double?) -> Unit = {}
) {
    val displayRating = rating ?: 0.0

    // Assign color based on rating range
    val starColor = when {
        rating == null -> Color.Gray
        rating >= 4.5 -> Rating5
        rating >= 3.5 -> Rating4
        rating >= 2.5 -> Rating3
        rating >= 1.5 -> Rating2
        else -> Rating1
    }

    val pointerModifier = if (isEditable) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val totalWidth = size.width
                val starWidth = totalWidth / 5f
                val tappedStarIndex = (offset.x / starWidth).toInt() // 0 to 4
                val fraction = offset.x % starWidth
                val isHalf = fraction < starWidth / 2f
                val newRating = tappedStarIndex + 1f - (if (isHalf) 0.5f else 0f)
                val clamped = newRating.coerceIn(1.0f, 5.0f).toDouble()
                onRatingChanged(clamped)
            }
        }
    } else {
        Modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.then(pointerModifier)
    ) {
        for (i in 1..5) {
            val icon = when {
                displayRating >= i -> Icons.Default.Star
                displayRating >= i - 0.5 -> Icons.Default.StarHalf
                else -> Icons.Default.StarBorder
            }
            Icon(
                imageVector = icon,
                contentDescription = if (isEditable) "Rate $i stars" else null,
                tint = starColor,
                modifier = Modifier.size(20.dp)
            )
        }

        if (rating != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format("%.1f/5.0", rating),
                color = starColor,
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelSmall
            )
        } else if (isEditable) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Unrated",
                color = Color.Gray,
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
