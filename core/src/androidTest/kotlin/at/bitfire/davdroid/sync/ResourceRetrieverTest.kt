/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class ResourceRetrieverTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @Inject
    lateinit var resourceRetrieverFactory: ResourceRetriever.Factory

    lateinit var account: Account

    @Before
    fun setUp() {
        hiltRule.inject()
        account = TestAccount.create()

        // add credentials to test account so that we can check whether they have been sent
        val settings = accountSettingsFactory.create(account)
        settings.credentials(Credentials("test", "test".toSensitiveString()))
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)
    }


    @Test
    fun testRetrieve_DataUri() = runTest {
        val downloader = resourceRetrieverFactory.create(account, "example.com")
        val result = downloader.retrieve("data:image/png;base64,dGVzdA==")
        assertArrayEquals("test".toByteArray(), result)
    }

    @Test
    fun testRetrieve_DataUri_Invalid() = runTest {
        val downloader = resourceRetrieverFactory.create(account, "example.com")
        val result = downloader.retrieve("data:;INVALID,INVALID")
        assertNull(result)
    }

    @Test
    fun testRetrieve_ExternalDomain() = runTest {
        val engine = MockEngine { respond("TEST", HttpStatusCode.OK) }
        val downloader = resourceRetrieverFactory.create(account, "example.com")
        // Request to a different domain — auth restriction is enforced by HttpClientBuilder.fromAccount()
        val result = downloader.retrieve("https://other-domain.example.net/photo.jpg", HttpClient(engine))
        assertArrayEquals("TEST".toByteArray(), result)
    }

    @Test
    fun testRetrieve_FtpUrl() = runTest {
        val downloader = resourceRetrieverFactory.create(account, "example.com")
        val result = downloader.retrieve("ftp://example.com/photo.jpg")
        assertNull(result)
    }

    @Test
    fun testRetrieve_RelativeHttpsUrl() = runTest {
        val downloader = resourceRetrieverFactory.create(account, "example.com")
        val result = downloader.retrieve("https:photo.jpg")
        assertNull(result)
    }

    @Test
    fun testRetrieve_SameDomain() = runTest {
        val engine = MockEngine { respond("TEST", HttpStatusCode.OK) }
        val downloader = resourceRetrieverFactory.create(account, "example.com")
        val result = downloader.retrieve("https://example.com/photo.jpg", HttpClient(engine))
        assertArrayEquals("TEST".toByteArray(), result)
    }

}
