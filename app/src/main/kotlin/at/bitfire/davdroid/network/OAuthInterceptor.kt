/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import okhttp3.Interceptor
import okhttp3.Response
import java.io.Closeable
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Creates an okhttp interceptor that sends an OAuth Bearer token authorization as described in RFC 6750.
 */
class OAuthInterceptor @AssistedInject constructor(
    @Assisted private val authState: AuthState,
    @Assisted private val callback: AuthStateUpdateCallback?,
    private val authService: AuthorizationService,
    private val logger: Logger
) : Interceptor, Closeable {

    @AssistedFactory
    interface Factory {
        fun create(authState: AuthState, callback: AuthStateUpdateCallback? = null): OAuthInterceptor
    }

    override fun close() {
        authService.dispose()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val rq = chain.request().newBuilder()

        try {
            val accessToken = provideAccessToken()
            logger.finer("Authenticating request with access token")
            rq.header("Authorization", "Bearer $accessToken")
        } catch (e: AuthorizationException) {
            logger.log(Level.WARNING, "Couldn't obtain access token", e)
        }

        return chain.proceed(rq.build())
    }

    /**
     * Provides a fresh access token.
     *
     * @throws AuthorizationException   if no access token can be provided
     */
    fun provideAccessToken(): String {
        val accessToken = CompletableDeferred<String>()

        // get access token
        authState.performActionWithFreshTokens(authService) { token: String?, _: String?, ex: AuthorizationException? ->
            if (token != null) {
                // persist updated AuthState
                callback?.onUpdate(authState)

                // emit access token
                accessToken.complete(token)

            } else if (ex != null)
                accessToken.completeExceptionally(ex)
        }

        return runBlocking {
            accessToken.await()
        }
    }


    fun interface AuthStateUpdateCallback {
        fun onUpdate(authState: AuthState)
    }

}