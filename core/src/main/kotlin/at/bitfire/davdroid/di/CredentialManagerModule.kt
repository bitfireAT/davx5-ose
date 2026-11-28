/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import android.content.Context
import androidx.credentials.CredentialManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/**
 * Provides the [CredentialManager]. Kept separate from [AndroidServicesModule] so that tests
 * which uninstall that module (to mock e.g. `AccountManager`) still get a `CredentialManager`.
 */
@Module
@InstallIn(SingletonComponent::class)
object CredentialManagerModule {

    @Provides
    fun credentialManager(@ApplicationContext context: Context): CredentialManager =
        CredentialManager.create(context)

}
