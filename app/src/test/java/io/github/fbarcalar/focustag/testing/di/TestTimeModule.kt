package io.github.fbarcalar.focustag.testing.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.fbarcalar.focustag.di.TimeModule
import io.github.fbarcalar.focustag.testing.FakeClock
import java.time.Clock
import javax.inject.Singleton

/** Every Hilt test runs on a [FakeClock]. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [TimeModule::class])
object TestTimeModule {
    @Provides
    @Singleton
    fun fakeClock(): FakeClock = FakeClock()

    @Provides
    fun clock(fake: FakeClock): Clock = fake
}
