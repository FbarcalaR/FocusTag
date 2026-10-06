package io.github.fbarcalar.focustag

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.fbarcalar.focustag.blocker.BlockListRepository
import io.github.fbarcalar.focustag.blocker.InstalledAppsSource
import io.github.fbarcalar.focustag.focus.AppStartHook
import io.github.fbarcalar.focustag.focus.FocusController
import io.github.fbarcalar.focustag.focus.FocusEffects
import io.github.fbarcalar.focustag.focus.FocusStateReader
import io.github.fbarcalar.focustag.nfc.NfcGateway
import io.github.fbarcalar.focustag.nfc.PairingRepository
import io.github.fbarcalar.focustag.nfc.TagWriter
import io.github.fbarcalar.focustag.system.GrayscaleFallbackSettings
import io.github.fbarcalar.focustag.system.PermissionChecker
import io.github.fbarcalar.focustag.testing.cancelApplicationScope
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HiltGraphTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var focusController: FocusController
    @Inject lateinit var focusStateReader: FocusStateReader
    @Inject lateinit var focusEffects: FocusEffects
    @Inject lateinit var appStartHooks: Set<@JvmSuppressWildcards AppStartHook>
    @Inject lateinit var nfcGateway: NfcGateway
    @Inject lateinit var pairingRepository: PairingRepository
    @Inject lateinit var tagWriter: TagWriter
    @Inject lateinit var permissionChecker: PermissionChecker
    @Inject lateinit var grayscaleFallbackSettings: GrayscaleFallbackSettings
    @Inject lateinit var blockListRepository: BlockListRepository
    @Inject lateinit var installedAppsSource: InstalledAppsSource

    @Before
    fun inject() = hiltRule.inject()

    @Test
    fun `the graph binds the controller and the reader to one engine`() {
        assertThat(focusController).isSameInstanceAs(focusStateReader)
    }

    @After
    fun endProcess() = cancelApplicationScope(composeRule.activity)

    @Test
    fun `the app launches to the setup screen when no tags are paired`() {
        val setupTitle = composeRule.activity.getString(R.string.title_setup)
        composeRule.waitUntilAtLeastOneExists(hasText(setupTitle), timeoutMillis = 5_000)

        composeRule.onAllNodesWithText(setupTitle).onFirst().assertIsDisplayed()
    }
}
