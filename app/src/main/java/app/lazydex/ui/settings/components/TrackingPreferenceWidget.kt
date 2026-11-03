package app.lazydex.ui.settings.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.R

@Composable
fun TrackLogoIcon(
    @DrawableRes logoResId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(logoResId),
        contentDescription = contentDescription,
        modifier = modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.medium)
    )
}

@Composable
fun TrackingPreferenceWidget(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes logoResId: Int = R.drawable.brand_anilist,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null, onClick = { onClick?.invoke() })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackLogoIcon(
            logoResId = logoResId,
            contentDescription = title,
            modifier = Modifier.clip(MaterialTheme.shapes.medium)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                fontSize = 16.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    fontSize = 13.sp
                )
            }
        }
        if (checked) {
            Icon(
                imageVector = Icons.Outlined.Done,
                modifier = Modifier
                    .padding(4.dp)
                    .size(28.dp),
                tint = Color(0xFF4CAF50),
                contentDescription = "Logged in"
            )
        }
    }
}

