/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Sends an OAuth Bearer token authorization as described in RFC 6750.
 */
class OAuthInterceptor @AssistedInject constructor(
    @Assisted private val authService: AuthorizationService,
    @Assisted private val authState: AuthState,
    @Assisted private val onAuthStateUpdate: AuthStateUpdateCallback?,
    private val logger: Logger
): Interceptor {

    @AssistedFactory
    interface Factory {
        fun create(authService: AuthorizationService, authState: AuthState, onAuthStateUpdate: AuthStateUpdateCallback?): OAuthInterceptor
    }


    override fun intercept(chain: Interceptor.Chain): Response {
        val rq = chain.request().newBuilder()

        provideAccessToken()?.let { accessToken ->
            rq.header("Authorization", "Bearer $accessToken")
        }

        return chain.proceed(rq.build())
    }

    @Synchronized
    fun provideAccessToken(): String? {
        val authStateStrBefore = authState.jsonSerializeString()

        // FIXME remove
        logger.log(Level.FINE, "AuthState before", authStateStrBefore)

        val accessTokenFuture = CompletableFuture<String>()
        authState.performActionWithFreshTokens(authService) { accessToken: String?, _: String?, ex: AuthorizationException? ->
            if (ex != null)
                accessTokenFuture.completeExceptionally(ex)
            else if (accessToken != null) {
                val authStateStrAfter = authState.jsonSerializeString()
                if (authStateStrBefore != authStateStrAfter) {
                    // FIXME remove
                    logger.log(Level.FINE, "AuthState after", authStateStrAfter)

                    // persist updated AuthState
                    onAuthStateUpdate?.onUpdate(authState)
                }

                // emit access token
                accessTokenFuture.complete(accessToken)
            } else
                accessTokenFuture.cancel(true)
        }

        return try {
            accessTokenFuture.get()
        } catch (e: ExecutionException) {
            logger.log(Level.SEVERE, "Couldn't obtain access token", e.cause)
            null
        }
    }


    fun interface AuthStateUpdateCallback {
        fun onUpdate(authState: AuthState)
    }

}