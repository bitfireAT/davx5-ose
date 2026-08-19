/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.os.DeadObjectException
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.local.LocalAddressBook
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.test.assertThrows
import io.ktor.http.Url
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger

class AddressBookSyncerTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    lateinit var accountManager: AccountManager

    @RelaxedMockK
    lateinit var logger: Logger

    private val addressBook: LocalAddressBook = mockk(relaxed = true)
    private val contactsSyncManager: ContactsSyncManager = mockk(relaxed = true)
    private val contactsSyncManagerFactory: ContactsSyncManager.Factory = mockk()
    private val provider: ContentProviderClient = mockk(relaxed = true)
    private val settings = SyncSettingsFixtures.default()
    private val syncResult = SyncResult()

    private val collection = Collection(
        id = 1,
        type = Collection.TYPE_ADDRESSBOOK,
        url = Url("https://example.com/addressbook/")
    )

    private lateinit var syncer: AddressBookSyncer

    @Before
    fun setUp() {
        syncer = AddressBookSyncer(
            accountId = mockk(relaxed = true),
            resync = null,
            syncFrameworkUpload = false,
            syncResult = syncResult,
            settings = settings,
            accountManager = { accountManager },
            addressBookStore = mockk(relaxed = true),
            contactsSyncManagerFactory = contactsSyncManagerFactory,
            ioDispatcher = Dispatchers.Unconfined
        ).apply {
            httpClientBuilder = mockk(relaxed = true)
            logger = this@AddressBookSyncerTest.logger
        }

        // group method hasn't changed, so that handleGroupMethodChange() doesn't do anything
        every { accountManager.getUserData(any(), any()) } returns settings.groupMethod.name

        every {
            contactsSyncManagerFactory.contactsSyncManager(
                accountId = any(),
                httpClient = any(),
                syncResult = any(),
                provider = any(),
                localAddressBook = any(),
                collectionInfo = any(),
                remoteCollection = any(),
                resync = any(),
                syncFrameworkUpload = any(),
                settings = any()
            )
        } returns contactsSyncManager
    }


    @Test
    fun testSyncCollection_rethrowsCancellation() = runTest {
        coEvery { contactsSyncManager.performSync() } throws CancellationException()

        /* Cancellation must not be swallowed here: only the Syncer/BaseSyncWorker may decide what to do
        about it (see issue #2663). */
        assertThrows<CancellationException> {
            syncer.syncCollection(provider, addressBook, collection)
        }
        assertFalse(syncResult.hasError)
    }

    @Test
    fun testSyncCollection_rethrowsDeadObjectException() = runTest {
        coEvery { contactsSyncManager.performSync() } throws
                LocalStorageException("Couldn't access local storage", DeadObjectException())

        // Same for the DeadObjectException, which the Syncer treats as soft error
        assertThrows<LocalStorageException> {
            syncer.syncCollection(provider, addressBook, collection)
        }
    }

}
