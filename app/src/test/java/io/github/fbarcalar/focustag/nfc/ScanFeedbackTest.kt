package io.github.fbarcalar.focustag.nfc

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.ScanOutcome
import io.github.fbarcalar.focustag.focus.TagRole
import org.junit.Test

class ScanFeedbackTest {
    @Test
    fun `every role and outcome maps to its message`() {
        val expected = mapOf(
            (TagRole.ACTIVATE to ScanOutcome.ACTIVATED) to ScanFeedback.FOCUS_ON,
            (TagRole.ACTIVATE to ScanOutcome.DEACTIVATED) to ScanFeedback.FREE_TIME,
            (TagRole.ACTIVATE to ScanOutcome.NO_CHANGE) to ScanFeedback.ALREADY_FOCUS,
            (TagRole.DEACTIVATE to ScanOutcome.ACTIVATED) to ScanFeedback.FOCUS_ON,
            (TagRole.DEACTIVATE to ScanOutcome.DEACTIVATED) to ScanFeedback.FREE_TIME,
            (TagRole.DEACTIVATE to ScanOutcome.NO_CHANGE) to ScanFeedback.ALREADY_FREE,
        )

        expected.forEach { (input, feedback) ->
            assertThat(ScanFeedback.of(input.first, input.second)).isEqualTo(feedback)
        }
    }
}
