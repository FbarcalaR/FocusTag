package io.github.fbarcalar.focustag.nfc.di

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import io.github.fbarcalar.focustag.nfc.PairingRepository
import io.github.fbarcalar.focustag.nfc.NfcTagWriter
import io.github.fbarcalar.focustag.nfc.TagPairingStore
import io.github.fbarcalar.focustag.nfc.TagWriter
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class NfcModule {
    @Binds
    abstract fun pairingRepository(store: TagPairingStore): PairingRepository

    @Binds
    abstract fun tagWriter(writer: NfcTagWriter): TagWriter

    companion object {
        private const val PAIRINGS_FILE = "tag_pairings"

        /** The DataStore stays private to the store, so no unqualified `DataStore` binding exists (R2.2). */
        @Provides
        @Singleton
        fun tagPairingStore(
            @ApplicationContext context: Context,
            @ApplicationScope appScope: CoroutineScope,
            @IoDispatcher io: CoroutineDispatcher,
        ): TagPairingStore = TagPairingStore(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = CoroutineScope(appScope.coroutineContext + io),
            ) { context.preferencesDataStoreFile(PAIRINGS_FILE) },
        )
    }
}
