/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.davdroid.BuildConfig
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Provider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Provides Ktor Bearer authentication (RFC 6750) with an OAuth access token.
 *
 * @param readAuthState     callback that fetches an up-to-date authorization state
 * @param writeAuthState    callback that persists a new authorization state
 */
class OAuthProvider @AssistedInject constructor(
    @Assisted private val readAuthState: () -> AuthState?,
    @Assisted private val writeAuthState: (AuthState) -> Unit,
    private val authServiceProvider: Provider<AuthorizationService>,
    private val logger: Logger
) {

    @AssistedFactory
    interface Factory {
        fun create(readAuthState: () -> AuthState?, writeAuthState: (AuthState) -> Unit): OAuthProvider
    }


    /**
     * Builds a Ktor [BearerAuthProvider] that sends the current OAuth access token as a Bearer
     * token on every request.
     */
    fun authProvider(): BearerAuthProvider = BearerAuthProvider(
        refreshTokens = { loadAccessToken() },
        loadTokens = { loadAccessToken() },
        realm = null,
        cacheTokens = false     // AuthState already tracks token freshness/expiry itself
    )

    /**
     * Provides fresh Bearer tokens for authorization. Uses the current access token if it's still
     * valid, or requests a new one if necessary.
     *
     * @return Bearer tokens, or `null` if no valid access token is available (usually because of an error during refresh)
     */
    private suspend fun loadAccessToken(): BearerTokens? {
        // if possible, use cached access token
        val authState = readAuthState() ?: return null

        if (authState.isAuthorized && authState.accessToken != null && !authState.needsTokenRefresh) {
            if (BuildConfig.DEBUG)      // log sensitive information (refresh/access token) only in debug builds
                logger.log(Level.FINEST, "Using cached AuthState", authState.jsonSerializeString())
            return BearerTokens(authState.accessToken!!, null)
        }

        // request fresh access token
        logger.fine("Requesting fresh access token")
        val authService = authServiceProvider.get()
        try {
            return suspendCancellableCoroutine { cont ->
                authState.performActionWithFreshTokens(authService) { accessToken: String?, _: String?, ex: AuthorizationException? ->
                    if (BuildConfig.DEBUG)
                        logger.log(Level.FINEST, "Got new AuthState", authState.jsonSerializeString())

                    // persist updated AuthState
                    writeAuthState(authState)

                    when {
                        ex != null -> cont.resumeWithException(ex)
                        accessToken != null -> cont.resume(BearerTokens(accessToken, null))
                        else -> cont.resume(null)
                    }
                }
            }
        } catch (e: AuthorizationException) {
            logger.log(Level.SEVERE, "Couldn't obtain access token", e)
            return null
        } finally {
            authService.dispose()
        }
    }

}
