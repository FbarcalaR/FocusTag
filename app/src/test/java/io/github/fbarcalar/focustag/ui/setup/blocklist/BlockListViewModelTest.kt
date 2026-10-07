package io.github.fbarcalar.focustag.ui.setup.blocklist

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.blocker.InstalledApp
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.testing.FakeFocusEngine
import io.github.fbarcalar.focustag.testing.MainDispatcherRule
import io.github.fbarcalar.focustag.ui.setup.FakeBlockListRepository
import io.github.fbarcalar.focustag.ui.setup.FakeInstalledAppsSource
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlockListViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule(StandardTestDispatcher())

    private val maps = InstalledApp("com.example.maps", "Maps")
    private val video = InstalledApp("com.example.video", "VideoTube")
    private val repository = FakeBlockListRepository(setOf(video.packageName))
    private val engine = FakeFocusEngine()

    private fun test(block: suspend TestScope.(BlockListViewModel) -> Unit) = runTest(mainRule.dispatcher) {
        val viewModel = BlockListViewModel(repository, FakeInstalledAppsSource(listOf(maps, video)), engine)
        backgroundScope.launch { viewModel.uiState.collect {} }
        block(viewModel)
    }

    private val BlockListViewModel.rows get() = (uiState.value.apps as AppsState.Loaded).rows

    private fun enterFocus() {
        engine.state.value = FocusState.Focus(Instant.EPOCH)
        repository.inFocus = true
    }

    @Test
    fun `apps load once and are marked blocked`() = test { viewModel ->
        val before = viewModel.uiState.value.apps
        runCurrent()

        assertThat(before).isEqualTo(AppsState.Loading)
        assertThat(viewModel.rows).containsExactly(
            AppRow(maps.packageName, "Maps", blocked = false, canToggle = true),
            AppRow(video.packageName, "VideoTube", blocked = true, canToggle = true),
        ).inOrder()
    }

    @Test
    fun `the query filters the rows`() = test { viewModel ->
        viewModel.onQueryChange("TUBE")
        runCurrent()

        assertThat(viewModel.rows.map { it.label }).containsExactly("VideoTube")
        assertThat(viewModel.uiState.value.query).isEqualTo("TUBE")
    }

    @Test
    fun `checking an app blocks it in free time and in focus`() = test { viewModel ->
        enterFocus()

        viewModel.setBlocked(maps.packageName, blocked = true)
        runCurrent()

        assertThat(repository.blockedPackages.value).contains(maps.packageName)
    }

    @Test
    fun `unchecking an app unblocks it in free time`() = test { viewModel ->
        viewModel.setBlocked(video.packageName, blocked = false)
        runCurrent()

        assertThat(repository.blockedPackages.value).isEmpty()
    }

    @Test
    fun `in focus a blocked app cannot be toggled`() = test { viewModel ->
        enterFocus()
        runCurrent()

        assertThat(viewModel.uiState.value.focusLocked).isTrue()
        assertThat(viewModel.rows.single { it.blocked }.canToggle).isFalse()
        assertThat(viewModel.rows.single { !it.blocked }.canToggle).isTrue()
    }

    @Test
    fun `a removal refused by the repository leaves the app blocked`() = test { viewModel ->
        repository.inFocus = true

        viewModel.setBlocked(video.packageName, blocked = false)
        runCurrent()

        assertThat(viewModel.rows.single { it.packageName == video.packageName }.blocked).isTrue()
    }
}
