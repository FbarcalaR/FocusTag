package io.github.fbarcalar.focustag.ui.status

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import io.github.fbarcalar.focustag.R
import java.util.Locale
import kotlin.time.Duration

/** `HH:MM:SS` with total hours (a 25 h session reads `25:00:00`); negative durations read as zero. */
internal fun formatClock(duration: Duration): String {
    val totalSeconds = duration.coerceAtLeast(Duration.ZERO).inWholeSeconds
    return String.format(Locale.ROOT, "%02d:%02d:%02d", totalSeconds / 3600, totalSeconds / 60 % 60, totalSeconds % 60)
}

/** TalkBack text for a timer: "01:05:03" would be read as a time of day. */
@Composable
internal fun spokenDuration(duration: Duration): String {
    val totalSeconds = duration.coerceAtLeast(Duration.ZERO).inWholeSeconds
    val hours = (totalSeconds / 3600).toInt()
    val minutes = (totalSeconds / 60 % 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return when {
        hours > 0 && minutes == 0 -> pluralStringResource(R.plurals.status_spoken_hours, hours, hours)
        hours > 0 -> listOf(
            pluralStringResource(R.plurals.status_spoken_hours, hours, hours),
            pluralStringResource(R.plurals.status_spoken_minutes, minutes, minutes),
        ).joinToString(", ")
        minutes > 0 -> pluralStringResource(R.plurals.status_spoken_minutes, minutes, minutes)
        else -> pluralStringResource(R.plurals.status_spoken_seconds, seconds, seconds)
    }
}
