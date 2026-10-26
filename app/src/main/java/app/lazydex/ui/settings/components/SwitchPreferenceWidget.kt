package app.lazydex.ui.settings.components

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SwitchPreferenceWidget(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    BasePreferenceWidget(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = if (enabled) { { onCheckedChange(!checked) } } else null,
        enabled = enabled,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
        },
        modifier = modifier
    )
}
