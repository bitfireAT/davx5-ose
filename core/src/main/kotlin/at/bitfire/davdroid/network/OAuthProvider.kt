/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.dav4jvm.ktor.DomainAuthProvider
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.network.OAuthProvider.Companion.refreshMutex
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Token refreshes are serialized via global [refreshMutex] because several [HttpClient]s (e.g. concurrent
 * calendar/task/contacts sync) can share one account and would otherwise race to refresh the same token.
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
            refreshTokens = { retrieveFreshAccessToken(oldTokens) },
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
            logger.log(Level.FINEST, "Using existing AuthState: ${authState.jsonSerializeString()}")
        }
        return BearerTokens(accessToken, authState.refreshToken)
    }

    /**
     * Requests a fresh access token. Ignores whether [AuthState] still considers the current token valid.
     *
     * Calls to this method are serialized by the global [refreshMutex].
     *
     * @param oldTokens the tokens Ktor cached and that just got rejected
     */
    private suspend fun retrieveFreshAccessToken(oldTokens: BearerTokens?): BearerTokens? = refreshMutex.withLock {
        // read persisted auth state
        val authState = readAuthState() ?: return@withLock null
        val currentAccessToken = authState.accessToken

        /* If the persisted access token is already different to the rejected one,
        another caller refreshed it while we were waiting for the lock, and we can simply return the new one. */
        if (currentAccessToken != null && currentAccessToken != oldTokens?.accessToken)
            return@withLock BearerTokens(currentAccessToken, authState.refreshToken)

        logger.fine("Forcing fresh access token after rejected request")
        authState.needsTokenRefresh = true

        val authService = authServiceProvider.get()
        try {
            // AppAuth's method is callback-based and blocks its own worker thread until it's done;
            // runInterruptible lets coroutine cancellation propagate as a thread interrupt.
            return@withLock runInterruptible(ioDispatcher) {
                val future = CompletableFuture<BearerTokens?>()
                authState.performActionWithFreshTokens(authService) { accessToken: String?, _: String?, ex: AuthorizationException? ->
                    if (BuildConfig.DEBUG) {
                        // log sensitive information (refresh/access token) only in debug builds
                        logger.log(Level.FINE, "Got new AuthState: ${authState.jsonSerializeString()}")
                    }

                    // persist updated AuthState
                    writeAuthState(authState)

                    when {
                        ex != null -> future.completeExceptionally(ex)
                        accessToken != null -> future.complete(BearerTokens(accessToken, authState.refreshToken))
                        else -> future.complete(null)
                    }
                }
                future.get()
            }
        } catch (e: ExecutionException) {
            logger.log(Level.SEVERE, "Couldn't obtain access token", e.cause)
            return@withLock null
        } finally {
            authService.dispose()
        }
    }


    companion object {

        /** to serialize refreshes across all OAuthProvider instances, e.g. concurrent clients for the same account */
        private val refreshMutex = Mutex()

    }

}
