package app.lazydex.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDate(millis: Long?): String {
    if (millis == null || millis <= 0) return "—"
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun formatRelativeTime(timestamp: Long?, now: Long = System.currentTimeMillis()): String? {
    if (timestamp == null || timestamp <= 0L) return null
    val diff = now - timestamp
    if (diff < 0L || diff < 60_000L) return "Just now"
    val minutes = diff / 60_000L
    if (minutes < 60L) return "${minutes}m ago"
    val hours = diff / 3_600_000L
    if (hours < 24L) return "${hours}h ago"
    val days = diff / 86_400_000L
    if (days < 7L) return "${days}d ago"
    val weeks = diff / (7 * 86_400_000L)
    if (diff < 30 * 86_400_000L) return "${weeks}w ago"
    val months = diff / (30 * 86_400_000L)
    if (diff < 365 * 86_400_000L) return "${months}mo ago"
    val years = diff / (365 * 86_400_000L)
    return "${years}y ago"
}
