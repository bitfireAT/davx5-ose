/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.cert4android.CustomCertManager
import at.bitfire.cert4android.CustomCertStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional

@Module
@InstallIn(SingletonComponent::class)
class Cert4AndroidModule {

    @Provides
    fun customCertManager(): Optional<CustomCertManager> = Optional.empty()

    @Provides
    fun customHostnameVerifier(): Optional<CustomCertManager.HostnameVerifier> = Optional.empty()

    @Provides
    fun customCertStore(): Optional<CustomCertStore> = Optional.empty()

}