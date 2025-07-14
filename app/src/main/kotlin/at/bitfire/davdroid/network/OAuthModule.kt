/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationService
import java.net.HttpURLConnection
import java.net.URL

@Module
@InstallIn(SingletonComponent::class)
object OAuthModule {

    /**
     * Make sure to call [AuthorizationService.dispose] when obtaining an instance.
     *
     * Creating an instance is expensive (involves CustomTabsManager), so don't create an
     * instance if not necessary (use Provider/Lazy).
     */
    @Provides
    fun authorizationService(@ApplicationContext context: Context): AuthorizationService =
        AuthorizationService(context,
            AppAuthConfiguration.Builder()
                .setConnectionBuilder { uri ->
                    val url = URL(uri.toString())
                    (url.openConnection() as HttpURLConnection).apply {
                        setRequestProperty("User-Agent", UserAgentInterceptor.userAgent)
                    }
                }.build()
        )

}