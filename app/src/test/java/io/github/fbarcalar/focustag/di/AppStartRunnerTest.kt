package io.github.fbarcalar.focustag.di

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.AppStartHook
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartRunnerTest {
    private val ignoreFailures = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + ignoreFailures)

    @Test
    fun `every hook runs even when another hook fails`() {
        val ran = mutableListOf<String>()
        val failing = AppStartHook { error("boom") }
        val working = AppStartHook { ran += "working" }

        AppStartRunner(linkedSetOf(failing, working), scope).run()

        assertThat(ran).containsExactly("working")
    }
}
