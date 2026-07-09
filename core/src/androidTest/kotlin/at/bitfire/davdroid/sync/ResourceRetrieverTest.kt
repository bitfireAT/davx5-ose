/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import at.bitfire.davdroid.MockEngineUtils.basic
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.settings.AccountManagerSettingsStore
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    lateinit var accountSettingsFactory: AccountManagerSettingsStore.Factory

    @Inject
    lateinit var resourceRetrieverFactory: ResourceRetriever.Factory

    @Inject
    lateinit var httpClientBuilder: HttpClientBuilder

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
        val retriever = resourceRetrieverFactory.create(account, "example.com")
        val result = retriever.retrieve("data:image/png;base64,dGVzdA==")
        assertArrayEquals("test".toByteArray(), result)
    }

    @Test
    fun testRetrieve_DataUri_Invalid() = runTest {
        val retriever = resourceRetrieverFactory.create(account, "example.com")
        val result = retriever.retrieve("data:;INVALID,INVALID")
        assertNull(result)
    }

    @Test
    fun testRetrieve_ExternalDomain() = runTest {
        MockEngine.basic("TEST").use { engine ->
            httpClientBuilder
                // fromAccount() restricts authentication to the given domain; build the test client the
                // same way production code does so that restriction is actually exercised.
                .fromAccount(account, authDomain = "example.com")
                .build(engine)
                .use { httpClient ->
                    val retriever = resourceRetrieverFactory.create(account, "example.com", httpClient)
                    // Request to a different domain than the account's — auth must not be sent
                    val result = retriever.retrieve("https://other-domain.example.net/photo.jpg")

                    val sentAuth = engine.requestHistory.first().headers[HttpHeaders.Authorization]
                    assertNull(sentAuth)

                    assertArrayEquals("TEST".toByteArray(), result)
                }
        }
    }

    @Test
    fun testRetrieve_SameDomain() = runTest {
        MockEngine.basic("TEST").use { engine ->
            httpClientBuilder
                .fromAccount(account, authDomain = "example.com")
                .build(engine)
                .use { httpClient ->
                    val retriever = resourceRetrieverFactory.create(account, "example.com", httpClient)
                    val result = retriever.retrieve("https://example.com/photo.jpg")

                    val sentAuth = engine.requestHistory.first().headers[HttpHeaders.Authorization]
                    assertEquals("Basic dGVzdDp0ZXN0", sentAuth)

                    assertArrayEquals("TEST".toByteArray(), result)
                }
        }
    }

    @Test
    fun testRetrieve_FtpUrl() = runTest {
        val retriever = resourceRetrieverFactory.create(account, "example.com")
        val result = retriever.retrieve("ftp://example.com/photo.jpg")
        assertNull(result)
    }

    @Test
    fun testRetrieve_RelativeHttpsUrl() = runTest {
        val retriever = resourceRetrieverFactory.create(account, "example.com")
        val result = retriever.retrieve("https:photo.jpg")
        assertNull(result)
    }

}
