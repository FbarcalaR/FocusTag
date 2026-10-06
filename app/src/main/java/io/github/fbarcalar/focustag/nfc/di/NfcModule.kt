package io.github.fbarcalar.focustag.nfc.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.fbarcalar.focustag.nfc.PairingRepository
import io.github.fbarcalar.focustag.nfc.PlaceholderPairingRepository
import io.github.fbarcalar.focustag.nfc.PlaceholderTagWriter
import io.github.fbarcalar.focustag.nfc.TagWriter

@Module
@InstallIn(SingletonComponent::class)
interface NfcModule {
    @Binds
    fun pairingRepository(repository: PlaceholderPairingRepository): PairingRepository

    @Binds
    fun tagWriter(writer: PlaceholderTagWriter): TagWriter
}
