/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.security

import at.bitfire.davdroid.settings.Credentials
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class CredentialsSerializerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var factory: CredentialsSerializer.Factory

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun authState() =
        AuthState.jsonDeserialize("""{"refreshToken":"secret-refresh-token","scope":"openid"}""")

    @Test
    fun testRoundTrip_AllFields() {
        val serializer = factory.create(1)
        val original = Credentials("user", "secret".toSensitiveString(), "alias", authState())

        val decoded = Json.decodeFromString(serializer, Json.encodeToString(serializer, original))

        assertEquals("user", decoded.username)
        assertEquals("secret", decoded.password?.asString())
        assertEquals("alias", decoded.certificateAlias)
        assertEquals("secret-refresh-token", decoded.authState?.refreshToken)
    }

    @Test
    fun testRoundTrip_OnlyUsername() {
        val serializer = factory.create(1)
        val decoded = Json.decodeFromString(serializer, Json.encodeToString(serializer, Credentials(username = "user")))
        assertEquals(Credentials(username = "user"), decoded)
    }

    @Test
    fun testSecretsAreNotStoredInPlaintext() {
        val serializer = factory.create(1)
        val json = Json.encodeToString(serializer, Credentials("user", "secret".toSensitiveString(), null, authState()))

        assertFalse(json.contains("secret"))
        assertFalse(json.contains("refreshToken"))
        assertTrue(json.contains("user"))
    }

    @Test
    fun testAuthStateBoundToMountId() {
        val json = Json.encodeToString(factory.create(1), Credentials(authState = authState()))

        assertNotNull(Json.decodeFromString(factory.create(1), json).authState)
        assertTrue(runCatching { Json.decodeFromString(factory.create(2), json) }.isFailure)
    }

}
