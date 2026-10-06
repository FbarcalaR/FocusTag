package io.github.fbarcalar.focustag.testing.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.fbarcalar.focustag.nfc.NfcGateway
import io.github.fbarcalar.focustag.nfc.di.NfcGatewayModule
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import javax.inject.Singleton

/** Every Hilt test talks to a [FakeNfcGateway] instead of NFC hardware (D-15). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NfcGatewayModule::class])
object TestNfcGatewayModule {
    @Provides
    @Singleton
    fun fakeNfcGateway(): FakeNfcGateway = FakeNfcGateway()

    @Provides
    fun nfcGateway(fake: FakeNfcGateway): NfcGateway = fake
}
