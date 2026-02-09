/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose.di

import android.content.Context
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.cert4android.CustomCertStore
import at.bitfire.cert4android.SettingsProvider
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.ForegroundTracker
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.internal.tls.OkHostnameVerifier
import java.util.Optional

/**
 * cert4android integration module
 */
@Module
@InstallIn(SingletonComponent::class)
class CustomCertManagerModule {

    @Provides
    fun customCertStore(@ApplicationContext context: Context): Optional<CustomCertStore> =
        Optional.of(CustomCertStore.getInstance(context))

    @Provides
    @Reusable
    fun customCertManager(
        customCertStore: Optional<CustomCertStore>,
        settings: SettingsManager
    ): Optional<CustomCertManager> =
        Optional.of(
            CustomCertManager(
            certStore = customCertStore.get(),
            settings = object : SettingsProvider {

                override val appInForeground: Boolean
                    get() = ForegroundTracker.inForeground.value

                override val trustSystemCerts: Boolean
                    get() = !settings.getBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES)

            }
        ))

    @Provides
    @Reusable
    fun customHostnameVerifier(
        customCertManager: Optional<CustomCertManager>
    ): Optional<CustomCertManager.HostnameVerifier> =
        Optional.of(customCertManager.get().HostnameVerifier(OkHostnameVerifier))

}