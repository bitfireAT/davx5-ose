/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import android.accounts.AccountManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * Provides Android system services that have no `@Inject` constructor, so callers depend on the
 * injected type instead of each caller creating its own instance via a static factory method.
 * This decouples call sites from how the service is obtained, gives every consumer the same
 * instance, and lets tests substitute a mock via `@BindValue` instead of mocking the static getter.
 */
@Module
@InstallIn(SingletonComponent::class)
object AndroidServicesModule {

    @Provides
    fun accountManager(@ApplicationContext context: Context): AccountManager =
        AccountManager.get(context)

}