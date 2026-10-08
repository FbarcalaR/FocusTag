package io.github.fbarcalar.focustag.blocker.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.fbarcalar.focustag.blocker.AlwaysAllowedResolver
import io.github.fbarcalar.focustag.blocker.AndroidAlwaysAllowedResolver
import io.github.fbarcalar.focustag.blocker.BlockListRepository
import io.github.fbarcalar.focustag.blocker.BlockListStore
import io.github.fbarcalar.focustag.blocker.InstalledAppsRepository
import io.github.fbarcalar.focustag.blocker.InstalledAppsSource
import io.github.fbarcalar.focustag.di.ApplicationScope
import io.github.fbarcalar.focustag.di.IoDispatcher
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
interface BlockerModule {
    @Binds
    fun blockListRepository(store: BlockListStore): BlockListRepository

    @Binds
    fun alwaysAllowedResolver(resolver: AndroidAlwaysAllowedResolver): AlwaysAllowedResolver

    @Binds
    fun installedAppsSource(repository: InstalledAppsRepository): InstalledAppsSource

    companion object {
        @Provides
        @Singleton
        @BlockListPreferences
        fun blockListDataStore(
            @ApplicationContext context: Context,
            @ApplicationScope appScope: CoroutineScope,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): DataStore<Preferences> {
            val scope = CoroutineScope(appScope.coroutineContext + ioDispatcher)
            return BlockListStore.createDataStore(scope) { context.preferencesDataStoreFile(FILE_NAME) }
        }

        private const val FILE_NAME = "block_list"
    }
}
