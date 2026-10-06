package io.github.fbarcalar.focustag.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.fbarcalar.focustag.focus.AppStartHook

/** Declares the hook set so the graph is valid before any layer contributes one (D-43). */
@Module
@InstallIn(SingletonComponent::class)
interface AppModule {
    @Multibinds
    fun appStartHooks(): Set<AppStartHook>
}
