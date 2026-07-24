package app.lazydex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KomikkuNamespaceTags(
    tags: List<String>,
    onClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
        ) {
            tags.forEach { rawTag ->
                val hasNamespace = rawTag.contains(':')
                val label = if (hasNamespace) {
                    val index = rawTag.indexOf(':')
                    val ns = rawTag.substring(0, index).trim()
                    val tag = rawTag.substring(index + 1).trim()
                    "$ns: $tag"
                } else {
                    rawTag
                }

                if (hasNamespace) {
                    ElevatedSuggestionChip(
                        onClick = { onClick(rawTag) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                } else {
                    SuggestionChip(
                        onClick = { onClick(rawTag) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    }
}
