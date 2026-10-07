package io.github.fbarcalar.focustag.blocker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.blocker.ui.BlockingScreen
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.focus.FocusStateReader
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Shown over a blocked app during FOCUS (D-20); leaves by going home, closes itself on FREE. */
@AndroidEntryPoint
class BlockingActivity : ComponentActivity() {
    @Inject
    lateinit var focusState: FocusStateReader

    @Inject
    lateinit var installedApps: InstalledAppsRepository

    private val blockedPackage = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockedPackage.value = intent.blockedPackage()
        onBackPressedDispatcher.addCallback(this, goHomeOnBack())
        setContent { FocusTagTheme { BlockingContent() } }
        finishWhenFree()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockedPackage.value = intent.blockedPackage()
    }

    @Composable
    private fun BlockingContent() {
        val packageName by blockedPackage
        val label by produceState<AppLabel>(AppLabel.Loading, packageName) { value = loadLabel(packageName) }
        when (val current = label) {
            AppLabel.Loading -> Unit
            is AppLabel.Known -> BlockingScreen(appLabel = current.name, onGoHome = ::goHome)
            AppLabel.Unknown -> BlockingScreen(stringResource(R.string.blocking_unknown_app), onGoHome = ::goHome)
        }
    }

    private suspend fun loadLabel(packageName: String): AppLabel =
        installedApps.label(packageName)?.let(AppLabel::Known) ?: AppLabel.Unknown

    private fun finishWhenFree() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                focusState.state.first { it is FocusState.Free }
                finish()
            }
        }
    }

    private fun goHomeOnBack() = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = goHome()
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)
        finish()
    }

    /** The blocked app's name; nothing is drawn while it loads, so no fallback flashes. */
    private sealed interface AppLabel {
        data object Loading : AppLabel

        data class Known(val name: String) : AppLabel

        data object Unknown : AppLabel
    }

    private fun Intent.blockedPackage(): String = getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()

    companion object {
        /** Starts the blocking screen for [packageName] from a non-activity context. */
        fun intent(context: Context, packageName: String): Intent = Intent(context, BlockingActivity::class.java)
            .putExtra(EXTRA_BLOCKED_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
