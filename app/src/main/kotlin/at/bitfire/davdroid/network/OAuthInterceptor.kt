/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.davdroid.BuildConfig
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Sends an OAuth Bearer token authorization as described in RFC 6750.
 *
 * @param authService           authorization service (this class won't close it, so this remains the responsibility of the caller)
 * @param authState             authorization state of the specific account/server
 * @param onAuthStateUpdate     called to persist a new authorization state
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

        val accessToken = provideAccessToken()
        if (accessToken != null)
            rq.header("Authorization", "Bearer $accessToken")
        else
            logger.severe("No access token available, won't authenticate")

        return chain.proceed(rq.build())
    }

    /**
     * Provides a fresh access token for authorization. Uses the current one if it's still valid,
     * or requests a new one if necessary.
     *
     * This method is synchronized / thread-safe so that it can be called for multiple HTTP requests at the same time.
     *
     * @return  access token or `null` if no valid access token is available (usually because of an error during refresh)
     */
    @Synchronized
    fun provideAccessToken(): String? {
        // if possible, use cached access token
        if (authState.isAuthorized && authState.accessToken != null && !authState.needsTokenRefresh) {
            if (BuildConfig.DEBUG)
                logger.log(Level.FINEST, "Using cached AuthState", authState.jsonSerializeString())
            return authState.accessToken
        }

        // request fresh access token
        logger.fine("Requesting fresh access token")
        val accessTokenFuture = CompletableFuture<String>()
        authState.performActionWithFreshTokens(authService) { accessToken: String?, _: String?, ex: AuthorizationException? ->
            // appauth internally fetches the new token over HttpURLConnection in an AsyncTask
            if (BuildConfig.DEBUG)
                logger.log(Level.FINEST, "Got new AuthState", authState.jsonSerializeString())

            // persist updated AuthState
            onAuthStateUpdate?.onUpdate(authState)

            if (ex != null)
                accessTokenFuture.completeExceptionally(ex)
            else if (accessToken != null)
                accessTokenFuture.complete(accessToken)
            else
                accessTokenFuture.cancel(false)
        }

        return try {
            accessTokenFuture.join()
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Couldn't obtain access token", if (e is CompletionException) e.cause else e)
            null
        }
    }


    fun interface AuthStateUpdateCallback {
        fun onUpdate(authState: AuthState)
    }

}