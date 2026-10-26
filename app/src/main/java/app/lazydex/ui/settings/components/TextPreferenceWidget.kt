package app.lazydex.ui.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun TextPreferenceWidget(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    BasePreferenceWidget(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        enabled = enabled,
        trailing = trailing,
        modifier = modifier
    )
}
