package io.github.fbarcalar.focustag.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.AppStartHook
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric only for `android.util.Log`. */
@RunWith(AndroidJUnit4::class)
class AppStartRunnerTest {
    @Test
    fun `a failing hook is swallowed and the other hooks still run`() = runTest {
        val ran = mutableListOf<String>()
        val failing = AppStartHook { error("boom") }
        val working = AppStartHook { ran += "working" }

        AppStartRunner(linkedSetOf(failing, working), this).run()
        testScheduler.advanceUntilIdle()

        assertThat(ran).containsExactly("working")
    }
}
