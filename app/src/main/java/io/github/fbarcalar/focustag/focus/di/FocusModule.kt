package io.github.fbarcalar.focustag.focus.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.fbarcalar.focustag.focus.FocusController
import io.github.fbarcalar.focustag.focus.FocusStateReader
import io.github.fbarcalar.focustag.focus.PlaceholderFocusEngine

@Module
@InstallIn(SingletonComponent::class)
interface FocusModule {
    @Binds
    fun focusController(engine: PlaceholderFocusEngine): FocusController

    @Binds
    fun focusStateReader(engine: PlaceholderFocusEngine): FocusStateReader
}
