package io.github.fbarcalar.focustag.blocker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.fbarcalar.focustag.blocker.BlockListRepository
import io.github.fbarcalar.focustag.blocker.InstalledAppsSource
import io.github.fbarcalar.focustag.blocker.PlaceholderBlockListRepository
import io.github.fbarcalar.focustag.blocker.PlaceholderInstalledAppsSource

@Module
@InstallIn(SingletonComponent::class)
interface BlockerModule {
    @Binds
    fun blockListRepository(repository: PlaceholderBlockListRepository): BlockListRepository

    @Binds
    fun installedAppsSource(source: PlaceholderInstalledAppsSource): InstalledAppsSource
}
