/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.dav4jvm.ktor.DomainAuthProvider
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Provider

/**
 * Provides Bearer authentication (RFC 6750) with an OAuth access token.
 *
 * @param readAuthState     callback that fetches an up-to-date authorization state
 * @param writeAuthState    callback that persists a new authorization state
 */
class OAuthProvider @AssistedInject constructor(
    @Assisted private val readAuthState: () -> AuthState?,
    @Assisted private val writeAuthState: (AuthState) -> Unit,
    private val authServiceProvider: Provider<AuthorizationService>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {

    @AssistedFactory
    interface Factory {
        fun create(readAuthState: () -> AuthState?, writeAuthState: (AuthState) -> Unit): OAuthProvider
    }

    /**
     * Builds a Ktor [DomainAuthProvider]-wrapped Bearer auth provider that sends the current OAuth
     * access token as a Bearer token.
     *
     * @param firstLevelDomain  restrict sending the token to this domain (and its subdomains); `null` for no restriction
     */
    fun authProvider(firstLevelDomain: String?): DomainAuthProvider {
        val bearerAuthProvider = BearerAuthProvider(
            loadTokens = { loadAccessToken() },
            refreshTokens = { retrieveFreshAccessToken() },
            realm = null,
            cacheTokens = true
        )
        return DomainAuthProvider(firstLevelDomain, insecurePreemptive = true, bearerAuthProvider)
    }

    /**
     * Provides the current access token, if any. Doesn't check whether it's expired — BearerAuthProvider calls
     * [retrieveFreshAccessToken] in that case.
     */
    private fun loadAccessToken(): BearerTokens? {
        val authState = readAuthState() ?: return null
        val accessToken = authState.accessToken ?: return null

        if (BuildConfig.DEBUG) {
            // log sensitive information (refresh/access token) only in debug builds
            logger.log(Level.FINEST, "Using existing AuthState", authState.jsonSerializeString())
        }
        return BearerTokens(accessToken, null)
    }

    /**
     * Forces a fresh access token, ignoring whether the [AuthState] locally still considers the
     * current one valid. Used as [BearerAuthProvider]'s `refreshTokens`, which is only called after
     * the server has already rejected the current token — so a local "not expired yet" check must
     * not short-circuit the refresh.
     */
    private suspend fun retrieveFreshAccessToken(): BearerTokens? {
        val authState = readAuthState() ?: return null
        logger.fine("Forcing fresh access token after rejected request")
        authState.needsTokenRefresh = true

        val authService = authServiceProvider.get()
        try {
            // AppAuth's method is callback-based and blocks its own worker thread until it's done;
            // runInterruptible lets coroutine cancellation propagate as a thread interrupt.
            return runInterruptible(ioDispatcher) {
                val future = CompletableFuture<BearerTokens?>()
                authState.performActionWithFreshTokens(authService) { accessToken: String?, _: String?, ex: AuthorizationException? ->
                    if (BuildConfig.DEBUG) {
                        // log sensitive information (refresh/access token) only in debug builds
                        logger.log(Level.FINE, "Got new AuthState", authState.jsonSerializeString())
                    }

                    // persist updated AuthState
                    writeAuthState(authState)

                    when {
                        ex != null -> future.completeExceptionally(ex)
                        accessToken != null -> future.complete(BearerTokens(accessToken, null))
                        else -> future.complete(null)
                    }
                }
                future.get()
            }
        } catch (e: ExecutionException) {
            logger.log(Level.SEVERE, "Couldn't obtain access token", e.cause)
            return null
        } finally {
            authService.dispose()
        }
    }

}
