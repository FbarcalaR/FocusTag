package io.github.fbarcalar.focustag.system.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.fbarcalar.focustag.di.ApplicationScope
import io.github.fbarcalar.focustag.di.IoDispatcher
import io.github.fbarcalar.focustag.focus.FocusEffects
import io.github.fbarcalar.focustag.system.GrayscaleFallbackSettings
import io.github.fbarcalar.focustag.system.PermissionChecker
import io.github.fbarcalar.focustag.system.SystemFocusEffects
import io.github.fbarcalar.focustag.system.PlaceholderPermissionChecker
import io.github.fbarcalar.focustag.system.grayscale.DataStoreGrayscaleFallbackSettings
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/** The system layer's own Preferences DataStore (`system_settings`); never bound unqualified. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SystemDataStore

@Module
@InstallIn(SingletonComponent::class)
interface SystemModule {
    @Binds
    fun focusEffects(effects: SystemFocusEffects): FocusEffects

    @Binds
    fun permissionChecker(checker: PlaceholderPermissionChecker): PermissionChecker

    @Binds
    fun grayscaleFallbackSettings(settings: DataStoreGrayscaleFallbackSettings): GrayscaleFallbackSettings

    companion object {
        private const val FILE = "system_settings"

        @Provides
        @Singleton
        @SystemDataStore
        fun systemDataStore(
            @ApplicationContext context: Context,
            @ApplicationScope appScope: CoroutineScope,
            @IoDispatcher io: CoroutineDispatcher,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(appScope.coroutineContext + io),
        ) { context.preferencesDataStoreFile(FILE) }
    }
}
