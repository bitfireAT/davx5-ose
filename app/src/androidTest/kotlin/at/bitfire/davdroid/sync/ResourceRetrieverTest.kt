/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.davdroid.util.SensitiveString.Companion.toSensitiveString
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.InetAddress
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
    lateinit var server: MockWebServer

    @Before
    fun setUp() {
        hiltRule.inject()
        server = MockWebServer().apply {
            start()
        }

        account = TestAccount.create()

        // add credentials to test account so that we can check whether they have been sent
        val settings = accountSettingsFactory.create(account)
        settings.credentials(Credentials("test", "test".toSensitiveString()))
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)
        server.close()
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
        val baseUrl = server.url("/")
        val localhostIp = InetAddress.getByName(baseUrl.host).hostAddress!!

        // URL should be http://localhost, replace with http://127.0.0.1 to have other domain
        val baseUrlIp = baseUrl.newBuilder()
            .host(localhostIp)
            .build()

        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("TEST"))

        val downloader = resourceRetrieverFactory.create(account, baseUrl.host)
        val result = downloader.retrieve(baseUrlIp.toString())

        // authentication was NOT sent because request is not for original domain
        val sentAuth = server.takeRequest().getHeader(HttpHeaders.Authorization)
        assertNull(sentAuth)

        // and result is OK
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
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("TEST"))

        val baseUrl = server.url("/")
        val downloader = resourceRetrieverFactory.create(account, baseUrl.host)
        val result = downloader.retrieve(baseUrl.toString())

        // authentication was sent
        val sentAuth = server.takeRequest().getHeader(HttpHeaders.Authorization)
        assertEquals("Basic dGVzdDp0ZXN0", sentAuth)

        // and result is OK
        assertArrayEquals("TEST".toByteArray(), result)
    }

}