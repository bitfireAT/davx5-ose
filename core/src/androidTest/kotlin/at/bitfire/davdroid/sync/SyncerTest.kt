/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.os.DeadObjectException
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.local.LocalDataStore
import at.bitfire.synctools.storage.LocalStorageClient
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.test.assertThrows
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Optional
import java.util.logging.Logger

class SyncerTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    lateinit var logger: Logger

    val dataStore: LocalTestStore = mockk(relaxed = true)
    val client: LocalStorageClient = mockk(relaxed = true)
    val syncResult = SyncResult()

    private val accountId = LegacyAccount(Account("test", "test"))

    @SpyK
    @InjectMockKs
    var syncer = TestSyncer(accountId, null, syncResult, SyncSettingsFixtures.default(), dataStore).apply {
        /* Dependencies that are only used by invoke(). They have to be set before the spy is created, because
        the spy only gets a copy of the fields, while the lazy syncNotificationManager still resolves them
        on the original object. */
        syncNotificationManagerFactory = mockk(relaxed = true)
        syncValidator = Optional.empty()
    }


    @Test
    fun testSync_prepare_fails() = runTest {
        every { syncer.prepare(client) } returns false
        coEvery { syncer.getSyncEnabledCollections() } returns emptyMap()

        // Should stop the sync after prepare returns false
        syncer.sync(client)
        verify(exactly = 1) { syncer.prepare(client) }
        coVerify(exactly = 0) { syncer.getSyncEnabledCollections() }
    }

    @Test
    fun testSync_prepare_succeeds() = runTest {
        every { syncer.prepare(client) } returns true
        coEvery { syncer.getSyncEnabledCollections() } returns emptyMap()

        // Should continue the sync after prepare returns true
        syncer.sync(client)
        verify(exactly = 1) { syncer.prepare(client) }
        coVerify(exactly = 1) { syncer.getSyncEnabledCollections() }
    }


    @Test
    fun testUpdateCollections_deletesCollection() = runTest {
        val localCollection = mockk<LocalTestCollection> {
            every { dbCollectionId } returns 0L
        }

        // Should delete the localCollection if dbCollection (remote) does not exist
        val localCollections = mutableListOf(localCollection)
        val result = syncer.updateCollections(mockk(), localCollections, emptyMap())
        verify(exactly = 1) { dataStore.delete(localCollection) }

        // Updated local collection list should be empty
        assertTrue(result.isEmpty())
    }

    @Test
    fun testUpdateCollections_updatesCollection() = runTest {
        val localCollection = mockk<LocalTestCollection> {
            every { dbCollectionId } returns 0L
        }
        val dbCollection = mockk<Collection> {
            every { id } returns 0L
        }
        val dbCollections = mapOf(0L to dbCollection)

        // Should update the localCollection if it exists
        val result = syncer.updateCollections(client, listOf(localCollection), dbCollections)
        verify(exactly = 1) { dataStore.update(accountId, client, localCollection, dbCollection) }

        // Updated local collection list should be same as input
        assertArrayEquals(arrayOf(localCollection), result.toTypedArray())
    }

    @Test
    fun testUpdateCollections_findsNewCollection() = runTest {
        val dbCollection = mockk<Collection> {
            every { id } returns 0L
        }
        val localCollections = listOf(mockk<LocalTestCollection> {
            every { dbCollectionId } returns 0L
        })
        val dbCollections = listOf(dbCollection)
        val dbCollectionsMap = mapOf(dbCollection.id to dbCollection)
        coEvery { syncer.createLocalCollections(client, dbCollections) } returns localCollections

        // Should return the new collection, because it was not updated
        val result = syncer.updateCollections(client, emptyList(), dbCollectionsMap)

        // Updated local collection list contain new entry
        assertEquals(1, result.size)
        assertEquals(dbCollection.id, result[0].dbCollectionId)
    }


    @Test
    fun testCreateLocalCollections() = runTest {
        val localCollection = mockk<LocalTestCollection>()
        val dbCollection = mockk<Collection>()
        coEvery { dataStore.create(client, dbCollection) } returns localCollection

        // Should return list of newly created local collections
        val result = syncer.createLocalCollections(client, listOf(dbCollection))
        assertEquals(listOf(localCollection), result)
    }


    @Test
    fun testSyncCollectionContents() = runTest {
        val dbCollection1 = mockk<Collection>()
        val dbCollection2 = mockk<Collection>()
        val dbCollections = mapOf(
            0L to dbCollection1,
            1L to dbCollection2
        )
        val localCollection1 = mockk<LocalTestCollection> { every { dbCollectionId } returns 0L }
        val localCollection2 = mockk<LocalTestCollection> { every { dbCollectionId } returns 1L }
        val localCollections = listOf(localCollection1, localCollection2)
        every { localCollection1.dbCollectionId } returns 0L
        every { localCollection2.dbCollectionId } returns 1L
        coEvery { syncer.syncCollection(client, any(), any()) } just runs

        // Should call the collection content sync on both collections
        syncer.syncCollectionContents(client, localCollections, dbCollections)
        coVerify(exactly = 1) { syncer.syncCollection(client, localCollection1, dbCollection1) }
        coVerify(exactly = 1) { syncer.syncCollection(client, localCollection2, dbCollection2) }
    }


    @Test
    fun testInvoke_cancellation_isRethrown() = runTest {
        every { dataStore.acquireLocalStorageClient(any()) } returns client
        coEvery { syncer.sync(client) } throws CancellationException()

        // Cancellation is not a sync error, but has to be passed on to the worker
        assertThrows<CancellationException> {
            syncer()
        }
        assertFalse(syncResult.hasError)
    }

    @Test
    fun testInvoke_deadObjectException_isSoftError() = runTest {
        every { dataStore.acquireLocalStorageClient(any()) } returns client
        coEvery { syncer.sync(client) } throws
                LocalStorageException("Couldn't access local storage", DeadObjectException(), softError = true)

        syncer()
        assertTrue(syncResult.softError)
        assertFalse(syncResult.hardError)
    }

    @Test
    fun testInvoke_unclassifiedException_isHardError() = runTest {
        every { dataStore.acquireLocalStorageClient(any()) } returns client
        coEvery { syncer.sync(client) } throws Exception("Some unexpected problem")

        syncer()
        assertTrue(syncResult.hardError)
        assertFalse(syncResult.softError)
    }


    // Test helpers

    class TestSyncer(
        accountId: AccountId,
        resyncType: ResyncType?,
        syncResult: SyncResult,
        settings: SyncSettings,
        theDataStore: LocalTestStore
    ) : Syncer<LocalTestStore, LocalTestCollection>(accountId, resyncType, syncResult, settings) {

        override val dataStore: LocalTestStore =
            theDataStore

        override val serviceType: String
            get() = throw NotImplementedError()

        override fun prepare(client: LocalStorageClient): Boolean =
            throw NotImplementedError()

        override fun getDbSyncCollections(serviceId: Long): List<Collection> =
            throw NotImplementedError()

        override suspend fun syncCollection(
            client: LocalStorageClient,
            localCollection: LocalTestCollection,
            remoteCollectionInfo: Collection
        ) {
            throw NotImplementedError()
        }

    }

    class LocalTestStore : LocalDataStore<LocalTestCollection> {

        override val authority: String
            get() = throw NotImplementedError()

        override fun acquireLocalStorageClient(throwOnMissingPermissions: Boolean): LocalStorageClient? {
            throw NotImplementedError()
        }

        override suspend fun create(
            client: LocalStorageClient,
            fromCollection: Collection
        ): LocalTestCollection? {
            throw NotImplementedError()
        }

        override fun getAll(
            accountId: AccountId,
            client: LocalStorageClient
        ): List<LocalTestCollection> {
            throw NotImplementedError()
        }

        override fun getByDbCollectionId(
            accountId: AccountId,
            client: LocalStorageClient,
            dbCollectionId: Long
        ): LocalTestCollection? {
            throw NotImplementedError()
        }

        override fun update(
            accountId: AccountId,
            client: LocalStorageClient,
            localCollection: LocalTestCollection,
            fromCollection: Collection
        ) {
            throw NotImplementedError()
        }

        override fun delete(localCollection: LocalTestCollection) {
            throw NotImplementedError()
        }

        override fun updateAccount(oldAccount: Account, newAccount: Account, client: LocalStorageClient?) {
            throw NotImplementedError()
        }

    }

}
