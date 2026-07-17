package app.lazydex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lazydex.domain.model.UserStatus
import app.lazydex.ui.theme.StatusCompleted
import app.lazydex.ui.theme.StatusDropped
import app.lazydex.ui.theme.StatusInProgress
import app.lazydex.ui.theme.StatusOnHold
import app.lazydex.ui.theme.StatusPlanTo

@Composable
fun StatusBadge(status: UserStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        UserStatus.READING, UserStatus.WATCHING, UserStatus.PLAYING -> StatusInProgress
        UserStatus.COMPLETED -> StatusCompleted
        UserStatus.ON_HOLD -> StatusOnHold
        UserStatus.DROPPED -> StatusDropped
        UserStatus.PLAN_TO -> StatusPlanTo
    }

    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = status.displayName,
            color = color,
            fontSize = 10.sp
        )
    }
}
