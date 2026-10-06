package io.github.fbarcalar.focustag

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.fbarcalar.focustag.di.AppStartRunner
import javax.inject.Inject

@HiltAndroidApp
class FocusTagApp : Application() {
    @Inject
    lateinit var appStartRunner: AppStartRunner

    override fun onCreate() {
        super.onCreate()
        appStartRunner.run()
    }
}
