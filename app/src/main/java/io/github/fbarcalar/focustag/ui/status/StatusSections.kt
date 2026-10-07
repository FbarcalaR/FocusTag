package io.github.fbarcalar.focustag.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.FocusMode
import kotlin.time.Duration

// Display-only sections: nothing here is clickable or reacts to gestures (D-45).

/** The mode in large text; the colour only repeats what the text says. */
@Composable
internal fun ModeIndicator(mode: FocusMode, modifier: Modifier = Modifier) {
    val focused = mode == FocusMode.FOCUS
    Surface(
        color = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(if (focused) R.string.status_mode_focus else R.string.status_mode_free),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(vertical = 40.dp, horizontal = 16.dp)
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        )
    }
}

/** Session timer (FOCUS only) and today's total. */
@Composable
internal fun SessionTimers(mode: FocusMode, currentSession: Duration, todayTotal: Duration, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (mode == FocusMode.FOCUS) TimerRow(stringResource(R.string.status_current_session), currentSession)
        TimerRow(stringResource(R.string.status_today_total), todayTotal)
    }
}

@Composable
private fun TimerRow(label: String, value: Duration) {
    val spoken = spokenDuration(value)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = formatClock(value),
            // Tabular digits keep the ticking timer from jittering.
            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.semantics { contentDescription = spoken },
        )
    }
}

/** What to scan next; the only way to change the mode is a tag. */
@Composable
internal fun ModeHint(mode: FocusMode, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(if (mode == FocusMode.FOCUS) R.string.status_hint_focus else R.string.status_hint_free),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
