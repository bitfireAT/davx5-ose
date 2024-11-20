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
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    var syncer = TestSyncer(mockk(relaxed = true), emptyArray(), SyncResult(), dataStore)


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
        val localCollection = mockk<LocalTestCollection>()
        every { localCollection.collectionUrl } returns "http://delete.the/collection"
        every { localCollection.title } returns "Collection to be deleted locally"

        // Should delete the localCollection if dbCollection (remote) does not exist
        val localCollections = mutableListOf(localCollection)
        val result = syncer.updateCollections(mockk(), localCollections, emptyMap())
        verify(exactly = 1) { dataStore.delete(localCollection) }

        // Updated local collection list should be empty
        assertTrue(result.isEmpty())
    }

    @Test
    fun testUpdateCollections_updatesCollection() {
        val localCollection = mockk<LocalTestCollection>()
        val dbCollection = mockk<Collection>()
        val dbCollections = mapOf("http://update.the/collection".toHttpUrl() to dbCollection)
        every { dbCollection.url } returns "http://update.the/collection".toHttpUrl()
        every { localCollection.collectionUrl } returns "http://update.the/collection"
        every { localCollection.title } returns "The Local Collection"

        // Should update the localCollection if it exists
        val result = syncer.updateCollections(provider, listOf(localCollection), dbCollections)
        verify(exactly = 1) { dataStore.update(provider, localCollection, dbCollection) }

        // Updated local collection list should be same as input
        assertArrayEquals(arrayOf(localCollection), result.toTypedArray())
    }

    @Test
    fun testUpdateCollections_findsNewCollection() {
        val dbCollection = mockk<Collection> {
            every { url } returns "http://newly.found/collection".toHttpUrl()
        }
        val localCollections = listOf(mockk<LocalTestCollection> {
            every { collectionUrl } returns "http://newly.found/collection"
        })
        val dbCollections = listOf(dbCollection)
        val dbCollectionsMap = mapOf(dbCollection.url to dbCollection)
        every { syncer.createLocalCollections(provider, dbCollections) } returns localCollections

        // Should return the new collection, because it was not updated
        val result = syncer.updateCollections(provider, emptyList(), dbCollectionsMap)

        // Updated local collection list contain new entry
        assertEquals(1, result.size)
        assertEquals(dbCollection.url.toString(), result[0].collectionUrl)
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
            "http://newly.found/collection1".toHttpUrl() to dbCollection1,
            "http://newly.found/collection2".toHttpUrl() to dbCollection2
        )
        val localCollection1 = mockk<LocalTestCollection>()
        val localCollection2 = mockk<LocalTestCollection>()
        val localCollections = listOf(localCollection1, localCollection2)
        every { localCollection1.collectionUrl } returns "http://newly.found/collection1"
        every { localCollection2.collectionUrl } returns "http://newly.found/collection2"
        every { syncer.syncCollection(provider, any(), any()) } just runs

        // Should call the collection content sync on both collections
        syncer.syncCollectionContents(provider, localCollections, dbCollections)
        verify(exactly = 1) { syncer.syncCollection(provider, localCollection1, dbCollection1) }
        verify(exactly = 1) { syncer.syncCollection(provider, localCollection2, dbCollection2) }
    }
    

    // Test helpers

    class TestSyncer (
        account: Account,
        extras: Array<String>,
        syncResult: SyncResult,
        theDataStore: LocalTestStore
    ) : Syncer<LocalTestStore, LocalTestCollection>(account, extras, syncResult) {

        override val dataStore: LocalTestStore =
            theDataStore

        override val authority: String
            get() = throw NotImplementedError()

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

        override fun create(
            provider: ContentProviderClient,
            fromCollection: Collection
        ): LocalTestCollection? {
            throw NotImplementedError()
        }

        override fun getAll(
            account: Account,
            provider: ContentProviderClient
        ): List<LocalTestCollection> {
            throw NotImplementedError()
        }

        override fun update(
            provider: ContentProviderClient,
            localCollection: LocalTestCollection,
            fromCollection: Collection
        ) {
            throw NotImplementedError()
        }

        override fun delete(localCollection: LocalTestCollection) {
            throw NotImplementedError()
        }

    }

}