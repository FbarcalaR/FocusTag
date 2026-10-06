package io.github.fbarcalar.focustag.nfc.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.fbarcalar.focustag.nfc.NfcGateway
import io.github.fbarcalar.focustag.nfc.PlaceholderNfcGateway

/** Hardware binding kept in its own module so `TestNfcGatewayModule` can replace it by class. */
@Module
@InstallIn(SingletonComponent::class)
interface NfcGatewayModule {
    @Binds
    fun nfcGateway(gateway: PlaceholderNfcGateway): NfcGateway
}
