package io.github.fbarcalar.focustag.system.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.fbarcalar.focustag.focus.FocusEffects
import io.github.fbarcalar.focustag.system.GrayscaleFallbackSettings
import io.github.fbarcalar.focustag.system.PermissionChecker
import io.github.fbarcalar.focustag.system.PlaceholderFocusEffects
import io.github.fbarcalar.focustag.system.PlaceholderGrayscaleFallbackSettings
import io.github.fbarcalar.focustag.system.PlaceholderPermissionChecker

@Module
@InstallIn(SingletonComponent::class)
interface SystemModule {
    @Binds
    fun focusEffects(effects: PlaceholderFocusEffects): FocusEffects

    @Binds
    fun permissionChecker(checker: PlaceholderPermissionChecker): PermissionChecker

    @Binds
    fun grayscaleFallbackSettings(settings: PlaceholderGrayscaleFallbackSettings): GrayscaleFallbackSettings
}
