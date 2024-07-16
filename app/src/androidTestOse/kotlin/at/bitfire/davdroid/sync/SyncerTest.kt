/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockk
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@HiltAndroidTest
class SyncerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val serviceRepository = mockk<DavServiceRepository>()
    private val collectionRepository = mockk<DavCollectionRepository>()

    /** use our WebDAV provider as a mock provider because it's our own and we don't need any permissions for it */
    private val mockAuthority = context.getString(R.string.webdav_authority)

    val account = Account(javaClass.canonicalName, context.getString(R.string.account_type))


    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testOnPerformSync_runsSyncAndSetsClassLoader() {
        val syncer = TestSyncer(context, serviceRepository, collectionRepository, account, arrayOf(), mockAuthority, SyncResult())
        syncer.onPerformSync()

        // check whether onPerformSync() actually calls sync()
        assertEquals(1, syncer.syncCalled.get())

        // check whether contextClassLoader is set
        assertEquals(context.classLoader, Thread.currentThread().contextClassLoader)
    }


    class TestSyncer(
        context: Context,
        serviceRepository: DavServiceRepository,
        collectionRepository: DavCollectionRepository,
        account: Account,
        extras: Array<String>,
        authority: String,
        syncResult: SyncResult
    ) : Syncer(context, serviceRepository, collectionRepository, account, extras, authority, syncResult) {

        val syncCalled = AtomicInteger()

        override fun sync() {
            Thread.sleep(1000)
            syncCalled.incrementAndGet()
        }

        override fun beforeSync() {}

        override fun afterSync() {}

        override fun getServiceType(): String {
            return ""
        }

        override fun getLocalResourceUrls(): List<HttpUrl?> {
            return emptyList()
        }

        override fun deleteLocalResource(url: HttpUrl?) {}

        override fun updateLocalResource(collection: Collection) {}

        override fun createLocalResource(collection: Collection) {}

        override fun getLocalSyncableResourceUrls(): List<HttpUrl?> {
            return emptyList()
        }

        override fun syncLocalResource(collection: Collection) {}

    }

}