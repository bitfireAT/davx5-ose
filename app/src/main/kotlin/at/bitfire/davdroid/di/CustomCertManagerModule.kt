/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import android.content.Context
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.cert4android.CustomCertStore
import at.bitfire.cert4android.SettingsProvider
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.ForegroundTracker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.internal.tls.OkHostnameVerifier
import java.util.Optional
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
/**
 * cert4android integration module
 */
class CustomCertManagerModule {

    @Provides
    @Singleton
    fun customCertManager(
        @ApplicationContext context: Context,
        settings: SettingsManager
    ): Optional<CustomCertManager> =
        if (BuildConfig.allowCustomCerts)
            Optional.of(
                CustomCertManager(
                certStore = CustomCertStore.getInstance(context),
                settings = object : SettingsProvider {

                    override val appInForeground: Boolean
                        get() = ForegroundTracker.inForeground.value

                    override val trustSystemCerts: Boolean
                        get() = !settings.getBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES)

                }
            ))
        else
            Optional.empty()

    @Provides
    @Singleton
    fun customHostnameVerifier(
        customCertManager: Optional<CustomCertManager>
    ): Optional<CustomCertManager.HostnameVerifier> =
        if (BuildConfig.allowCustomCerts && customCertManager.isPresent) {
            val hostnameVerifier = customCertManager.get().HostnameVerifier(OkHostnameVerifier)
            Optional.of(hostnameVerifier)
        } else
            Optional.empty()

}