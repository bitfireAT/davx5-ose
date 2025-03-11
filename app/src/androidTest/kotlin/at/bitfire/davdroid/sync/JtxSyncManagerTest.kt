/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.provider.ContactsContract
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class JtxSyncManagerTest {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var httpClientBuilder: HttpClient.Builder

    @Inject
    lateinit var jtxSyncManagerFactory: JtxSyncManager.Factory

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

    private lateinit var account: Account
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setUpWorkManager(context, workerFactory)

        account = TestAccount.create()

        server = MockWebServer().apply {
            start()
        }
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)

        // clear annoying syncError notifications
        NotificationManagerCompat.from(context).cancelAll()

        server.close()
    }


    @Test
    fun test_recurrenceIdWithoutDtStart() {
        val collection = LocalJtxCollection(account, provider, 0).apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "ctag1")
        }
        val syncManager = syncManager(collection)
    }


    // helpers

    private fun syncManager(
        localCollection: LocalJtxCollection,
        syncResult: SyncResult = SyncResult(),
        collection: Collection = mockk<Collection> {
            every { id } returns 1
            every { url } returns server.url("/")
        }
    ) = jtxSyncManagerFactory.jtxSyncManager(
        account,
        arrayOf(),
        httpClientBuilder.build(),
        "TestAuthority",
        syncResult,
        localCollection,
        collection
    )


    companion object {

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        private lateinit var provider: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun connect() {
            val context = InstrumentationRegistry.getInstrumentation().context
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
        }

        @AfterClass
        @JvmStatic
        fun disconnect() {
            provider.close()
        }
    }

}
