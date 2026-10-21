package com.ashis.notesphare.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val MARKUP_REGEX = Regex("""\*\*|__|~~|`|\*""")
private val BULLET_REGEX = Regex("""(?m)^\s*[-*]\s+""")
private val WHITESPACE_REGEX = Regex("""\s+""")

fun stripMarkup(text: String): String = text
    .replace(MARKUP_REGEX, "")
    .replace(BULLET_REGEX, "")
    .trim()

fun noteSnippet(content: String, maxLength: Int = 140): String {
    val plain = stripMarkup(content).replace(WHITESPACE_REGEX, " ").trim()
    if (plain.length <= maxLength) return plain
    return plain.take(maxLength).trimEnd() + "\u2026"
}

fun formatRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0L) return ""
    val timestampCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val nowCalendar = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = timestampCalendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR)
    val sameDay = isSameDay(timestampCalendar, nowCalendar)
    if (sameDay) {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -1)
    }
    if (isSameDay(timestampCalendar, yesterday)) return "Yesterday"
    val delta = now - timestamp
    if (delta in 0..(6L * 24 * 60 * 60 * 1000)) {
        return SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
    }
    val pattern = if (sameYear) "MMM d" else "MMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(timestamp))

fun formatFullDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))

private fun isSameDay(first: Calendar, second: Calendar): Boolean =
    first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe >= 1024L * 1024L * 1024L ->
            String.format(Locale.US, "%.1f GB", safe / (1024.0 * 1024.0 * 1024.0))
        safe >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", safe / (1024.0 * 1024.0))
        safe >= 1024L -> String.format(Locale.US, "%.0f KB", safe / 1024.0)
        else -> "$safe B"
    }
}
