package app.lazydex.ui.statistics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lazydex.ui.components.Badge
import app.lazydex.ui.components.BadgeGroup
import app.lazydex.ui.components.CoverImage
import app.lazydex.ui.statistics.TopRatedMediaCardData
import app.lazydex.ui.theme.getRatingColor
import java.util.Locale

@Composable
fun TopRatedGallery(
    items: List<TopRatedMediaCardData>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = items,
            key = { it.id }
        ) { item ->
            TopRatedCompactCard(
                item = item,
                onClick = { onItemClick(item.id) }
            )
        }
    }
}

@Composable
private fun TopRatedCompactCard(
    item: TopRatedMediaCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(104.dp)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        CoverImage(
            coverImagePath = item.coverImagePath,
            coverImageUrl = item.coverImageUrl,
            title = item.title,
            shape = RoundedCornerShape(6.dp),
            isObscured = item.isObscured,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f)
                        )
                    )
                )
        )

        BadgeGroup(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
        ) {
            Badge(
                text = String.format(Locale.US, "★ %.1f", item.rating),
                color = getRatingColor(item.rating),
                textColor = MaterialTheme.colorScheme.onPrimary
            )
        }

        Text(
            text = item.title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color.White,
                shadow = Shadow(
                    color = Color.Black,
                    blurRadius = 4f
                )
            )
        )
    }
}
