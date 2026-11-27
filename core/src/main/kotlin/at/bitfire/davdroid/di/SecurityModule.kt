/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import android.content.Context
import at.bitfire.davdroid.security.CredentialCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class SecurityModule {

    @Provides
    @Singleton
    fun credentialCipherModule(@ApplicationContext context: Context): CredentialCipher = CredentialCipher(context)

}
