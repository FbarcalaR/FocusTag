package io.github.fbarcalar.focustag.focus.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.fbarcalar.focustag.di.ApplicationScope
import io.github.fbarcalar.focustag.di.IoDispatcher
import io.github.fbarcalar.focustag.focus.FocusController
import io.github.fbarcalar.focustag.focus.FocusEngine
import io.github.fbarcalar.focustag.focus.FocusReconciler
import io.github.fbarcalar.focustag.focus.FocusStateReader
import io.github.fbarcalar.focustag.focus.notification.AndroidFocusNotifier
import io.github.fbarcalar.focustag.focus.notification.FocusNotifier
import io.github.fbarcalar.focustag.focus.store.FocusStateStore
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** Binds the focus engine. No `DataStore<Preferences>` enters the graph, so other layers' stores cannot clash. */
@Module
@InstallIn(SingletonComponent::class)
interface FocusModule {
    @Binds
    fun focusController(engine: FocusEngine): FocusController

    @Binds
    fun focusStateReader(engine: FocusEngine): FocusStateReader

    @Binds
    fun focusReconciler(engine: FocusEngine): FocusReconciler

    @Binds
    fun focusNotifier(notifier: AndroidFocusNotifier): FocusNotifier

    companion object {
        @Provides
        @Singleton
        fun focusStateStore(
            @ApplicationContext context: Context,
            @ApplicationScope appScope: CoroutineScope,
            @IoDispatcher io: CoroutineDispatcher,
        ): FocusStateStore = FocusStateStore.create(CoroutineScope(appScope.coroutineContext + io)) {
            context.preferencesDataStoreFile(STORE_FILE)
        }

        private const val STORE_FILE = "focus_state"
    }
}
