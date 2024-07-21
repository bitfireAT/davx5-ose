/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import okhttp3.Interceptor
import okhttp3.Response
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Sends an OAuth Bearer token authorization as described in RFC 6750.
 */
class BearerAuthInterceptor(
    private val accessToken: String
): Interceptor {

    companion object {

        val logger: Logger
            get() = Logger.getGlobal()

        fun fromAuthState(authService: AuthorizationService, authState: AuthState, callback: AuthStateUpdateCallback? = null): BearerAuthInterceptor? {
            return runBlocking {
                val accessTokenFuture = CompletableDeferred<String>()

                authState.performActionWithFreshTokens(authService) { accessToken: String?, _: String?, ex: AuthorizationException? ->
                    if (accessToken != null) {
                        // persist updated AuthState
                        callback?.onUpdate(authState)

                        // emit access token
                        accessTokenFuture.complete(accessToken)
                    }
                    else {
                        logger.log(Level.WARNING, "Couldn't obtain access token", ex)
                        accessTokenFuture.cancel()
                    }
                }

                // return value
                try {
                    BearerAuthInterceptor(accessTokenFuture.await())
                } catch (ignored: CancellationException) {
                    null
                }
            }
        }

    }

    override fun intercept(chain: Interceptor.Chain): Response {
        logger.finer("Authenticating request with access token")
        val rq = chain.request().newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()
        return chain.proceed(rq)
    }


    fun interface AuthStateUpdateCallback {
        fun onUpdate(authState: AuthState)
    }

}