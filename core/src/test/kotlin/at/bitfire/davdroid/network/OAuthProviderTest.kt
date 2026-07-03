/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.net.Uri
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.util.logging.Logger

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)      // required because main project uses Conscrypt, but unit tests do not
class OAuthProviderTest {

    @get:Rule
    val mockKRule = MockKRule(this)

    @MockK(relaxed = true)
    lateinit var authService: AuthorizationService

    private val config = AuthorizationServiceConfiguration(
        Uri.parse("https://example.com/auth"),
        Uri.parse("https://example.com/token")
    )
    private val logger = Logger.getLogger(javaClass.name)

    private fun oAuthProvider(
        readAuthState: () -> AuthState?,
        writeAuthState: (AuthState) -> Unit = {},
        firstLevelDomain: String? = null
    ) =
        OAuthProvider(
            readAuthState = readAuthState,
            writeAuthState = writeAuthState,
            authServiceProvider = { authService },
            logger = logger
        ).authProvider(firstLevelDomain)

    @Test
    fun `addRequestHeaders() with cached, still valid access token`() = runTest {
        // Set refresh and access token
        val tokenRequest = TokenRequest.Builder(config, "client-id")
            .setRefreshToken("refresh-token")
            .build()
        val tokenResponse = TokenResponse.Builder(tokenRequest)
            .setAccessToken("access-token")
            .setAccessTokenExpirationTime(System.currentTimeMillis() + 3_600_000)
            .build()
        val authState = AuthState().apply {
            update(tokenResponse, null)
        }
        assertFalse(authState.needsTokenRefresh)

        val authProvider = oAuthProvider(readAuthState = { authState })
        val httpRequestBuilder = HttpRequestBuilder()

        authProvider.addRequestHeaders(httpRequestBuilder)

        assertEquals("Bearer access-token", httpRequestBuilder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() with expired access token and refresh token available`() = runTest {
        // Set refresh and expired access token. Refreshing the token also needs service configuration and client ID.
        val authRequest = AuthorizationRequest.Builder(
            config, "client-id", ResponseTypeValues.CODE, Uri.parse("https://app.example/redirect")
        ).build()
        val authResponse = AuthorizationResponse.Builder(authRequest).build()
        val tokenRequest = TokenRequest.Builder(config, "client-id")
            .setAuthorizationCode("code")
            .setRedirectUri(Uri.parse("https://app.example/redirect"))
            .build()
        val tokenResponse = TokenResponse.Builder(tokenRequest)
            .setAccessToken("access-token")
            .setRefreshToken("refresh-token")
            .build()
        val authState = AuthState(authResponse, tokenResponse, null).apply {
            needsTokenRefresh = true
        }
        assertTrue(authState.isAuthorized)
        assertEquals("refresh-token", authState.refreshToken)

        // Prepare AuthService so that it returns a new access token upon request
        every { authService.performTokenRequest(any(), any(), any()) } answers {
            val request = firstArg<TokenRequest>()
            val callback = thirdArg<AuthorizationService.TokenResponseCallback>()
            val response = TokenResponse.Builder(request)
                .setAccessToken("new-access-token")
                .build()
            callback.onTokenRequestCompleted(response, null)
        }

        // Create OAuthProvider that interacts with our prepared authState
        var newAuthState: AuthState? = null
        val authProvider = oAuthProvider(
            readAuthState = { authState },
            writeAuthState = { newAuthState = it }
        )
        val httpRequestBuilder = HttpRequestBuilder()

        authProvider.addRequestHeaders(httpRequestBuilder)

        assertEquals("Bearer new-access-token", httpRequestBuilder.headers[HttpHeaders.Authorization])
        assertEquals("new-access-token", newAuthState?.accessToken)
    }

    @Test
    fun `addRequestHeaders() without AuthState`() = runTest {
        val authProvider = oAuthProvider(readAuthState = { null })
        val httpRequestBuilder = HttpRequestBuilder()

        authProvider.addRequestHeaders(httpRequestBuilder)

        assertNull(httpRequestBuilder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `refreshToken() forces a fresh token even if the local state still considers the current one valid`() =
        runTest {
            // access token that is not locally expired, but a refresh token is available
            val authRequest = AuthorizationRequest.Builder(
                config, "client-id", ResponseTypeValues.CODE, Uri.parse("https://app.example/redirect")
            ).build()
            val authResponse = AuthorizationResponse.Builder(authRequest).build()
            val tokenRequest = TokenRequest.Builder(config, "client-id")
                .setAuthorizationCode("code")
                .setRedirectUri(Uri.parse("https://app.example/redirect"))
                .build()
            val tokenResponse = TokenResponse.Builder(tokenRequest)
                .setAccessToken("access-token")
                .setRefreshToken("refresh-token")
                .setAccessTokenExpirationTime(System.currentTimeMillis() + 3_600_000)
                .build()
            val authState = AuthState(authResponse, tokenResponse, null)
            assertFalse(authState.needsTokenRefresh)

            // AuthService returns a new access token upon request
            every { authService.performTokenRequest(any(), any(), any()) } answers {
                val request = firstArg<TokenRequest>()
                val callback = thirdArg<AuthorizationService.TokenResponseCallback>()
                val response = TokenResponse.Builder(request)
                    .setAccessToken("new-access-token")
                    .build()
                callback.onTokenRequestCompleted(response, null)
            }

            val authProvider = oAuthProvider(readAuthState = { authState })

            // populate the cache with the still-locally-valid token
            val firstRequest = HttpRequestBuilder()
            authProvider.addRequestHeaders(firstRequest)
            assertEquals("Bearer access-token", firstRequest.headers[HttpHeaders.Authorization])

            // server rejects that token (401) -> refreshToken() must not just re-return the cached one
            val refreshed = authProvider.refreshToken(mockk<HttpResponse>(relaxed = true))
            assertTrue(refreshed)
            verify { authService.performTokenRequest(any(), any(), any()) }

            // subsequent requests use the newly-fetched token
            val secondRequest = HttpRequestBuilder()
            authProvider.addRequestHeaders(secondRequest)
            assertEquals("Bearer new-access-token", secondRequest.headers[HttpHeaders.Authorization])
        }

    @Test
    fun `addRequestHeaders() with request hostname matching domain`() = runTest {
        val tokenRequest = TokenRequest.Builder(config, "client-id")
            .setRefreshToken("refresh-token")
            .build()
        val tokenResponse = TokenResponse.Builder(tokenRequest)
            .setAccessToken("access-token")
            .setAccessTokenExpirationTime(System.currentTimeMillis() + 3_600_000)
            .build()
        val authState = AuthState().apply {
            update(tokenResponse, null)
        }

        val authProvider = oAuthProvider(readAuthState = { authState }, firstLevelDomain = "domain.example")
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "subdomain.domain.example"
        }

        authProvider.addRequestHeaders(httpRequestBuilder)

        assertEquals("Bearer access-token", httpRequestBuilder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() with request hostname not matching domain`() = runTest {
        val tokenRequest = TokenRequest.Builder(config, "client-id")
            .setRefreshToken("refresh-token")
            .build()
        val tokenResponse = TokenResponse.Builder(tokenRequest)
            .setAccessToken("access-token")
            .setAccessTokenExpirationTime(System.currentTimeMillis() + 3_600_000)
            .build()
        val authState = AuthState().apply {
            update(tokenResponse, null)
        }

        val authProvider = oAuthProvider(readAuthState = { authState }, firstLevelDomain = "domain.example")
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "other-domain.example"
        }

        authProvider.addRequestHeaders(httpRequestBuilder)

        assertNull(httpRequestBuilder.headers[HttpHeaders.Authorization])
    }

}
