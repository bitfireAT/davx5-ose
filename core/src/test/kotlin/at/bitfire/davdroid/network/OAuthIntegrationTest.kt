/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.app.Application
import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], application = Application::class)
class OAuthIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val integration = OAuthIntegration(context)
    private val service = mockk<AuthorizationService>()
    private val response = AuthorizationResponse.Builder(
        AuthorizationRequest.Builder(
            AuthorizationServiceConfiguration(
                "https://example.com/auth".toUri(),
                "https://example.com/token".toUri()
            ),
            "client",
            ResponseTypeValues.CODE,
            integration.redirectUri
        ).build()
    ).setAuthorizationCode("code").build()

    @Test
    fun `empty callback completes authenticate with a failure`() = runTest {
        every { service.performTokenRequest(any(), any<AuthorizationService.TokenResponseCallback>()) } answers {
            secondArg<AuthorizationService.TokenResponseCallback>().onTokenRequestCompleted(null, null)
        }
        try {
            integration.authenticate(service, response)
            fail("Expected terminal failure")
        } catch (e: IllegalStateException) {
            assertEquals("OAuth token exchange returned no result", e.message)
        }
    }

    @Test
    fun `callback preserves the provider authorization failure`() = runTest {
        val error = AuthorizationException.TokenRequestErrors.INVALID_GRANT
        every { service.performTokenRequest(any(), any<AuthorizationService.TokenResponseCallback>()) } answers {
            secondArg<AuthorizationService.TokenResponseCallback>().onTokenRequestCompleted(null, error)
        }
        try {
            integration.authenticate(service, response)
            fail("Expected authorization failure")
        } catch (e: AuthorizationException) {
            assertSame(error, e)
        }
    }

    @Test
    fun `successful callback still returns the exchanged authorization state`() = runTest {
        every { service.performTokenRequest(any(), any<AuthorizationService.TokenResponseCallback>()) } answers {
            secondArg<AuthorizationService.TokenResponseCallback>().onTokenRequestCompleted(
                TokenResponse.Builder(firstArg<TokenRequest>()).setAccessToken("access-token").build(), null
            )
        }
        assertEquals("access-token", integration.authenticate(service, response).accessToken)
    }
}
