/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltAndroidTest
class SyncAdapterTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settingsManager: SettingsManager

    val context by lazy { InstrumentationRegistry.getInstrumentation().context }
    val targetContext by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

    /** use our WebDAV provider as a mock provider because it's our own and we don't need any permissions for it */
    val mockAuthority = targetContext.getString(R.string.webdav_authority)
    val mockProvider = context.contentResolver.acquireContentProviderClient(mockAuthority)!!

    val account = Account("test", "com.example.test")

    @Inject lateinit var db: AppDatabase
    lateinit var syncAdapter: TestSyncAdapter


    @Before
    fun setUp() {
        hiltRule.inject()

        syncAdapter = TestSyncAdapter(context, db)
    }


    @Test
    fun testPriorityCollections() {
        assertTrue(SyncAdapterService.SyncAdapter.priorityCollections(Bundle()).isEmpty())
        assertArrayEquals(arrayOf(1L,2L), SyncAdapterService.SyncAdapter.priorityCollections(Bundle(1).apply {
            putString(SyncAdapterService.SYNC_EXTRAS_PRIORITY_COLLECTIONS, "1,error,2")
        }).toTypedArray())
    }


    @Test
    fun testOnPerformSync_allowsSequentialSyncs() {
        for (i in 0 until 5)
            syncAdapter.onPerformSync(account, Bundle(), mockAuthority, mockProvider, SyncResult())
        assertEquals(5, syncAdapter.syncCalled.get())
    }

    @Test
    fun testOnPerformSync_allowsSimultaneousSyncs() {
        val extras = Bundle(1)
        extras.putLong(TestSyncAdapter.EXTRA_WAIT, 100)    // sync takes 100 ms

        val syncThreads = mutableListOf<Thread>()
        for (i in 0 until 100) {                            // within 100 ms, at least 2 threads should be created and run simultaneously
            syncThreads += Thread({
                syncAdapter.onPerformSync(account, extras, "$mockAuthority-$i", mockProvider, SyncResult())
            }).apply {
                start()
            }
        }

        // wait for all threads
        syncThreads.forEach { it.join() }

        assertEquals(100, syncAdapter.syncCalled.get())
    }

    @Test
    fun testOnPerformSync_preventsDuplicateSyncs() {
        val extras = Bundle(1)
        extras.putLong(TestSyncAdapter.EXTRA_WAIT, 500)    // sync takes 500 ms

        val syncThreads = mutableListOf<Thread>()
        for (i in 0 until 100) {        // creating 100 threads should be possible within in 500 ms
            syncThreads += Thread({
                syncAdapter.onPerformSync(account, extras, mockAuthority, mockProvider, SyncResult())
            }).apply {
                start()
            }
        }

        // wait for all threads
        syncThreads.forEach { it.join() }

        assertEquals(1, syncAdapter.syncCalled.get())
    }

    @Test
    fun testOnPerformSync_runsSyncAndSetsClassLoader() {
        syncAdapter.onPerformSync(account, Bundle(), mockAuthority, mockProvider, SyncResult())

        // check whether onPerformSync() actually calls sync()
        assertEquals(1, syncAdapter.syncCalled.get())

        // check whether contextClassLoader is set
        assertEquals(context.classLoader, Thread.currentThread().contextClassLoader)
    }


    class TestSyncAdapter(context: Context, db: AppDatabase): SyncAdapterService.SyncAdapter(context, db) {

        companion object {
            /**
             * How long the sync() method shall wait
             */
            val EXTRA_WAIT = "waitMillis"
        }

        val syncCalled = AtomicInteger()

        override fun sync(
            account: Account,
            extras: Bundle,
            authority: String,
            httpClient: Lazy<HttpClient>,
            provider: ContentProviderClient,
            syncResult: SyncResult
        ) {
            val wait = extras.getLong(EXTRA_WAIT)
            Thread.sleep(wait)

            syncCalled.incrementAndGet()
        }

    }

}