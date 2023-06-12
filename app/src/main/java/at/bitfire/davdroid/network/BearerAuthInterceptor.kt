/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.davdroid.log.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import okhttp3.Interceptor
import okhttp3.Response
import java.util.logging.Level

/**
 * Sends an OAuth Bearer token authorization as described in RFC 6750.
 */
class BearerAuthInterceptor(
    private val accessToken: String
): Interceptor {

    companion object {

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
                        Logger.log.log(Level.WARNING, "Couldn't obtain access token", ex)
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
        Logger.log.finer("Authenticating request with access token")
        val rq = chain.request().newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()
        return chain.proceed(rq)
    }


    fun interface AuthStateUpdateCallback {
        fun onUpdate(authState: AuthState)
    }

}