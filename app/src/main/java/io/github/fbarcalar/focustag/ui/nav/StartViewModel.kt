package io.github.fbarcalar.focustag.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.fbarcalar.focustag.nfc.PairingRepository
import io.github.fbarcalar.focustag.nfc.isComplete
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Where the app opens; decided once from the stored pairings. */
sealed interface StartDestination {
    data object Loading : StartDestination
    data object Setup : StartDestination
    data object Status : StartDestination
}

@HiltViewModel
class StartViewModel @Inject constructor(pairingRepository: PairingRepository) : ViewModel() {
    val startDestination: StateFlow<StartDestination> =
        flow {
            val complete = pairingRepository.pairings.first().isComplete()
            emit(if (complete) StartDestination.Status else StartDestination.Setup)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, StartDestination.Loading)
}
