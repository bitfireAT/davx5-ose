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
import net.openid.appauth.AuthorizationService

@Module
@InstallIn(SingletonComponent::class)
object OAuthModule {

    @Provides
    fun authorizationService(@ApplicationContext context: Context): AuthorizationService = AuthorizationService(context)

}