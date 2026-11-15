package app.lazydex.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.lazydex.domain.model.TypeConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexTypeTabs(
    tabs: List<TypeConfig>,
    selectedIndex: Int,
    getItemCount: (TypeConfig) -> Int?,
    onTabItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return
    val currentPageIndex = selectedIndex.coerceIn(0, tabs.lastIndex)

    Column(modifier = modifier.zIndex(2f)) {
        PrimaryScrollableTabRow(
            selectedTabIndex = currentPageIndex,
            edgePadding = 0.dp,
            divider = {},
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = currentPageIndex == index,
                    onClick = { onTabItemClick(index) },
                    text = {
                        DexTabText(
                            text = tab.name,
                            badgeCount = getItemCount(tab),
                        )
                    },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider()
    }
}

@Composable
fun DexTabText(
    text: String,
    badgeCount: Int? = null,
    modifier: Modifier = Modifier
) {
    val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (badgeCount != null) {
            DexTabPill(
                text = "$badgeCount",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
fun DexTabPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 10.sp,
) {
    Surface(
        modifier = modifier.padding(start = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = color,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                maxLines = 1,
                style = LocalTextStyle.current.merge(fontSize = fontSize),
            )
        }
    }
}
