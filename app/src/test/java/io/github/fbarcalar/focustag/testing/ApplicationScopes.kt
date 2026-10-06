package io.github.fbarcalar.focustag.testing

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.fbarcalar.focustag.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ApplicationScopeEntryPoint {
    @ApplicationScope
    fun applicationScope(): CoroutineScope
}

/**
 * Ends the test's "process": cancels the `@ApplicationScope` and waits, so DataStores, receivers
 * and collectors of one test cannot leak into the next. Call from `@After` in Hilt tests that
 * used the graph.
 */
fun cancelApplicationScope(context: Context) = runBlocking {
    val entryPoint = EntryPointAccessors.fromApplication(context, ApplicationScopeEntryPoint::class.java)
    entryPoint.applicationScope().coroutineContext.job.cancelAndJoin()
}
