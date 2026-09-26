package app.lazydex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.UserStatus
import app.lazydex.ui.theme.StatusCompleted
import app.lazydex.ui.theme.StatusDropped
import app.lazydex.ui.theme.StatusInProgress
import app.lazydex.ui.theme.StatusOnHold
import app.lazydex.ui.theme.StatusPlanTo

fun UserStatus.icon(): ImageVector = when (this) {
    UserStatus.READING, UserStatus.WATCHING, UserStatus.PLAYING -> Icons.Default.LocalLibrary
    UserStatus.PLAN_TO -> Icons.Default.BookmarkBorder
    UserStatus.COMPLETED -> Icons.Default.TaskAlt
    UserStatus.ON_HOLD -> Icons.Default.PauseCircle
    UserStatus.DROPPED -> Icons.Default.Cancel
}

fun UserStatus.color(): Color = when (this) {
    UserStatus.READING, UserStatus.WATCHING, UserStatus.PLAYING -> StatusInProgress
    UserStatus.COMPLETED -> StatusCompleted
    UserStatus.ON_HOLD -> StatusOnHold
    UserStatus.DROPPED -> StatusDropped
    UserStatus.PLAN_TO -> StatusPlanTo
}

@Composable
fun StatusBadge(status: UserStatus, modifier: Modifier = Modifier) {
    val color = status.color()
    val icon = status.icon()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = status.displayName,
            color = color,
            fontSize = 10.sp
        )
    }
}
