/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * Provides AndroidX support library singletons that have no `@Inject` constructor, so callers depend on the
 * injected type instead of each caller creating its own instance via a static factory method.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupportLibsModule {

    @Provides
    fun workManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

}
