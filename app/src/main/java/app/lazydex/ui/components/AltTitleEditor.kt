package app.lazydex.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AltTitleEditor(
    mainTitle: String,
    alternativeTitles: List<String>,
    onMainTitleChanged: (String) -> Unit,
    onAltTitlesChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (alternativeTitles.isNotEmpty()) {
            Text(
                text = "Alternative Titles",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        alternativeTitles.forEachIndexed { index, altTitle ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                OutlinedTextField(
                    value = altTitle,
                    onValueChange = { newValue ->
                        val updated = alternativeTitles.toMutableList()
                        updated[index] = newValue
                        onAltTitlesChanged(updated)
                    },
                    label = { Text("Alt Title ${index + 1}", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))

                // Swap button: Swaps altTitle with mainTitle
                IconButton(
                    onClick = {
                        val newMainTitle = altTitle
                        val updatedAlts = alternativeTitles.toMutableList()
                        updatedAlts[index] = mainTitle
                        onMainTitleChanged(newMainTitle)
                        onAltTitlesChanged(updatedAlts)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Swap with main title",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Remove button
                IconButton(
                    onClick = {
                        val updated = alternativeTitles.toMutableList()
                        updated.removeAt(index)
                        onAltTitlesChanged(updated)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove alternative title",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedButton(
            onClick = {
                onAltTitlesChanged(alternativeTitles + "")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Alternative Title", fontSize = 13.sp)
        }
    }
}
