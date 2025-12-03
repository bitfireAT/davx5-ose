/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.dav4jvm.HttpUtils.toKtorUrl
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
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.InetAddress
import javax.inject.Inject

@HiltAndroidTest
class ResourceDownloaderTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @Inject
    lateinit var resourceDownloaderFactory: ResourceDownloader.Factory

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
    fun testDownload_ExternalDomain() = runTest {
        val baseUrl = server.url("/")

        // URL should be http://localhost, replace with http://127.0.0.1 to have other domain
        Assume.assumeTrue(baseUrl.host == "localhost")
        val baseUrlIp = baseUrl.newBuilder()
            .host(InetAddress.getByName(baseUrl.host).hostAddress!!)
            .build()

        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("TEST"))

        val downloader = resourceDownloaderFactory.create(account, baseUrl.host)
        val result = downloader.download(baseUrlIp.toKtorUrl())

        // authentication was NOT sent because request is not for original domain
        val sentAuth = server.takeRequest().getHeader(HttpHeaders.Authorization)
        assertNull(sentAuth)

        // and result is OK
        assertArrayEquals("TEST".toByteArray(), result)
    }

    @Test
    fun testDownload_SameDomain() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("TEST"))

        val baseUrl = server.url("/")
        val downloader = resourceDownloaderFactory.create(account, baseUrl.host)
        val result = downloader.download(baseUrl.toKtorUrl())

        // authentication was sent
        val sentAuth = server.takeRequest().getHeader(HttpHeaders.Authorization)
        assertEquals("Basic dGVzdDp0ZXN0", sentAuth)

        // and result is OK
        assertArrayEquals("TEST".toByteArray(), result)
    }

}