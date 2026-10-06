package io.github.fbarcalar.focustag.e2e

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.fbarcalar.focustag.blocker.BlockListRepository
import io.github.fbarcalar.focustag.di.AppStartRunner
import io.github.fbarcalar.focustag.focus.FocusController
import io.github.fbarcalar.focustag.focus.FocusStateReader
import io.github.fbarcalar.focustag.nfc.PairingRepository
import io.github.fbarcalar.focustag.system.PermissionChecker
import io.github.fbarcalar.focustag.testing.FakeClock
import io.github.fbarcalar.focustag.testing.FakeNfcGateway

/** What the E2E harness reads from the real graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HarnessEntryPoint {
    fun focusController(): FocusController
    fun focusStateReader(): FocusStateReader
    fun pairingRepository(): PairingRepository
    fun blockListRepository(): BlockListRepository
    fun permissionChecker(): PermissionChecker
    fun fakeNfcGateway(): FakeNfcGateway
    fun fakeClock(): FakeClock
    fun appStartRunner(): AppStartRunner
}
