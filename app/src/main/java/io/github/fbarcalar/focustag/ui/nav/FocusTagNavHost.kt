package io.github.fbarcalar.focustag.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.fbarcalar.focustag.ui.setup.SetupDestination
import io.github.fbarcalar.focustag.ui.status.StatusDestination

/** Root navigation: Setup until both tags are paired, otherwise Status. */
@Composable
fun FocusTagNavHost(viewModel: StartViewModel = hiltViewModel()) {
    val start by viewModel.startDestination.collectAsStateWithLifecycle()
    when (start) {
        StartDestination.Loading -> Surface(Modifier.fillMaxSize()) {}
        StartDestination.Setup -> FocusTagNavGraph(startRoute = SetupRoute)
        StartDestination.Status -> FocusTagNavGraph(startRoute = StatusRoute)
    }
}

@Composable
private fun FocusTagNavGraph(startRoute: Any, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = startRoute) {
        composable<StatusRoute> {
            StatusDestination(onOpenSetup = { navController.navigate(SetupRoute) { launchSingleTop = true } })
        }
        composable<SetupRoute> {
            SetupDestination(
                onBack = navController.backActionOrNull(),
                onPairingComplete = { navController.showStatusAfterPairing() },
            )
        }
    }
}

private fun NavHostController.backActionOrNull(): (() -> Unit)? =
    if (previousBackStackEntry == null) null else ({ popBackStack() })

private fun NavHostController.showStatusAfterPairing() {
    navigate(StatusRoute) {
        popUpTo<SetupRoute> { inclusive = true }
        launchSingleTop = true
    }
}
