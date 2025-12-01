/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.LocalDataStore
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.logging.Logger

class SyncerTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @RelaxedMockK
    lateinit var logger: Logger

    val dataStore: LocalTestStore = mockk(relaxed = true)
    val provider: ContentProviderClient = mockk(relaxed = true)

    @SpyK
    @InjectMockKs
    var syncer = TestSyncer(mockk(relaxed = true), null, SyncResult(), dataStore)


    @Test
    fun testSync_prepare_fails() {
        every { syncer.prepare(provider) } returns false
        every { syncer.getSyncEnabledCollections() } returns emptyMap()

        // Should stop the sync after prepare returns false
        syncer.sync(provider)
        verify(exactly = 1) { syncer.prepare(provider) }
        verify(exactly = 0) { syncer.getSyncEnabledCollections() }
    }

    @Test
    fun testSync_prepare_succeeds() {
        every { syncer.prepare(provider) } returns true
        every { syncer.getSyncEnabledCollections() } returns emptyMap()

        // Should continue the sync after prepare returns true
        syncer.sync(provider)
        verify(exactly = 1) { syncer.prepare(provider) }
        verify(exactly = 1) { syncer.getSyncEnabledCollections() }
    }


    @Test
    fun testUpdateCollections_deletesCollection() {
        val localCollection = mockk<LocalTestCollection> {
            every { dbCollectionId } returns 0L
            every { title } returns "Collection to be deleted locally"
        }

        // Should delete the localCollection if dbCollection (remote) does not exist
        val localCollections = mutableListOf(localCollection)
        val result = syncer.updateCollections(mockk(), localCollections, emptyMap())
        verify(exactly = 1) { dataStore.delete(localCollection) }

        // Updated local collection list should be empty
        assertTrue(result.isEmpty())
    }

    @Test
    fun testUpdateCollections_updatesCollection() {
        val localCollection = mockk<LocalTestCollection> {
            every { dbCollectionId } returns 0L
            every { title } returns "The Local Collection"
        }
        val dbCollection = mockk<Collection> {
            every { id } returns 0L
        }
        val dbCollections = mapOf(0L to dbCollection)

        // Should update the localCollection if it exists
        val result = syncer.updateCollections(provider, listOf(localCollection), dbCollections)
        verify(exactly = 1) { dataStore.update(provider, localCollection, dbCollection) }

        // Updated local collection list should be same as input
        assertArrayEquals(arrayOf(localCollection), result.toTypedArray())
    }

    @Test
    fun testUpdateCollections_findsNewCollection() {
        val dbCollection = mockk<Collection> {
            every { id } returns 0L
        }
        val localCollections = listOf(mockk<LocalTestCollection> {
            every { dbCollectionId } returns 0L
        })
        val dbCollections = listOf(dbCollection)
        val dbCollectionsMap = mapOf(dbCollection.id to dbCollection)
        every { syncer.createLocalCollections(provider, dbCollections) } returns localCollections

        // Should return the new collection, because it was not updated
        val result = syncer.updateCollections(provider, emptyList(), dbCollectionsMap)

        // Updated local collection list contain new entry
        assertEquals(1, result.size)
        assertEquals(dbCollection.id, result[0].dbCollectionId)
    }


    @Test
    fun testCreateLocalCollections() {
        val localCollection = mockk<LocalTestCollection>()
        val dbCollection = mockk<Collection>()
        every { dataStore.create(provider, dbCollection) } returns localCollection

        // Should return list of newly created local collections
        val result = syncer.createLocalCollections(provider, listOf(dbCollection))
        assertEquals(listOf(localCollection), result)
    }


    @Test
    fun testSyncCollectionContents() {
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
        every { syncer.syncCollection(provider, any(), any()) } just runs

        // Should call the collection content sync on both collections
        syncer.syncCollectionContents(provider, localCollections, dbCollections)
        verify(exactly = 1) { syncer.syncCollection(provider, localCollection1, dbCollection1) }
        verify(exactly = 1) { syncer.syncCollection(provider, localCollection2, dbCollection2) }
    }
    

    // Test helpers

    class TestSyncer(
        account: Account,
        resyncType: ResyncType?,
        syncResult: SyncResult,
        theDataStore: LocalTestStore
    ) : Syncer<LocalTestStore, LocalTestCollection>(account, resyncType, syncResult) {

        override val dataStore: LocalTestStore =
            theDataStore

        override val serviceType: String
            get() = throw NotImplementedError()

        override fun prepare(provider: ContentProviderClient): Boolean =
            throw NotImplementedError()

        override fun getDbSyncCollections(serviceId: Long): List<Collection> =
            throw NotImplementedError()

        override fun syncCollection(
            provider: ContentProviderClient,
            localCollection: LocalTestCollection,
            remoteCollection: Collection
        ) {
            throw NotImplementedError()
        }

    }

    class LocalTestStore : LocalDataStore<LocalTestCollection> {

        override val authority: String
            get() = throw NotImplementedError()

        override fun acquireContentProvider(throwOnMissingPermissions: Boolean): ContentProviderClient? {
            throw NotImplementedError()
        }

        override fun create(
            client: ContentProviderClient,
            fromCollection: Collection
        ): LocalTestCollection? {
            throw NotImplementedError()
        }

        override fun getAll(
            account: Account,
            client: ContentProviderClient
        ): List<LocalTestCollection> {
            throw NotImplementedError()
        }

        override fun update(
            client: ContentProviderClient,
            localCollection: LocalTestCollection,
            fromCollection: Collection
        ) {
            throw NotImplementedError()
        }

        override fun delete(localCollection: LocalTestCollection) {
            throw NotImplementedError()
        }

        override fun updateAccount(oldAccount: Account, newAccount: Account, client: ContentProviderClient?) {
            throw NotImplementedError()
        }

    }

}