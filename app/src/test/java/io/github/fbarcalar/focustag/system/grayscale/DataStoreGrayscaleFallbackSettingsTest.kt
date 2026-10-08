package io.github.fbarcalar.focustag.system.grayscale

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreGrayscaleFallbackSettingsTest {
    @get:Rule
    val storeRule = SystemStoreRule()

    @Test
    fun `the fallback is off by default`() = runTest {
        assertThat(DataStoreGrayscaleFallbackSettings(storeRule.store).enabled.first()).isFalse()
    }

    @Test
    fun `the toggle survives a new process`() = runTest {
        DataStoreGrayscaleFallbackSettings(storeRule.store).setEnabled(true)

        val reopened = DataStoreGrayscaleFallbackSettings(storeRule.reopen())

        assertThat(reopened.enabled.first()).isTrue()
    }
}
