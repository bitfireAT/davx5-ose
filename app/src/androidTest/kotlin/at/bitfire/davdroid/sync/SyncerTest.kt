/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SyncerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var testSyncer: TestSyncer.Factory

    lateinit var account: Account

    private lateinit var syncer: TestSyncer

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccountAuthenticator.create()
        syncer = spyk(testSyncer.create(account, emptyArray(), SyncResult()))
    }

    @After
    fun tearDown() {
        TestAccountAuthenticator.remove(account)
    }


    @Test
    fun testSync_prepare_fails() {
        val provider = mockk<ContentProviderClient>()
        every { syncer.prepare(provider) } returns false
        every { syncer.getSyncEnabledCollections() } returns emptyMap()

        // Should stop the sync after prepare returns false
        syncer.sync(provider)
        verify(exactly = 1) { syncer.prepare(provider) }
        verify(exactly = 0) { syncer.getSyncEnabledCollections() }
    }

    @Test
    fun testSync_prepare_succeeds() {
        val provider = mockk<ContentProviderClient>()
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
        every { localCollection.deleteCollection() } returns true
        every { localCollection.title } returns "Collection to be deleted locally"

        // Should delete the localCollection if dbCollection (remote) does not exist
        val localCollections = mutableListOf(localCollection)
        val result = syncer.updateCollections(mockk(), localCollections, emptyMap())
        verify(exactly = 1) { localCollection.deleteCollection() }

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
        val result = syncer.updateCollections(mockk(), listOf(localCollection), dbCollections)
        verify(exactly = 1) { syncer.update(localCollection, dbCollection) }

        // Updated local collection list should be same as input
        assertArrayEquals(arrayOf(localCollection), result.toTypedArray())
    }

    @Test
    fun testUpdateCollections_findsNewCollection() {
        val dbCollection = mockk<Collection>()
        every { dbCollection.url } returns "http://newly.found/collection".toHttpUrl()
        val dbCollections = mapOf(dbCollection.url to dbCollection)

        // Should return the new collection, because it was not updated
        val result = syncer.updateCollections(mockk(), emptyList(), dbCollections)

        // Updated local collection list contain new entry
        assertEquals(1, result.size)
        assertEquals(dbCollection.url.toString(), result[0].collectionUrl)
    }


    @Test
    fun testCreateLocalCollections() {
        val provider = mockk<ContentProviderClient>()
        val localCollection = mockk<LocalTestCollection>()
        val dbCollection = mockk<Collection>()
        every { syncer.create(provider, dbCollection) } returns localCollection
        every { dbCollection.url } returns "http://newly.found/collection".toHttpUrl()

        // Should return list of newly created local collections
        val result = syncer.createLocalCollections(provider, listOf(dbCollection))
        assertEquals(listOf(localCollection), result)
    }

    
    @Test
    fun testSyncCollectionContents() {
        val provider = mockk<ContentProviderClient>()
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

        // Should call the collection content sync on both collections
        syncer.syncCollectionContents(provider, localCollections, dbCollections)
        verify(exactly = 1) { syncer.syncCollection(provider, localCollection1, dbCollection1) }
        verify(exactly = 1) { syncer.syncCollection(provider, localCollection2, dbCollection2) }
    }
    

    // Test helpers

    class TestSyncer @AssistedInject constructor(
        @Assisted account: Account,
        @Assisted extras: Array<String>,
        @Assisted syncResult: SyncResult
    ) : Syncer<LocalTestCollection>(account, extras, syncResult) {

        @AssistedFactory
        interface Factory {
            fun create(account: Account, extras: Array<String>, syncResult: SyncResult): TestSyncer
        }

        override val authority: String
            get() = ""
        override val serviceType: String
            get() = ""

        override fun prepare(provider: ContentProviderClient): Boolean =
            true

        override fun getLocalCollections(provider: ContentProviderClient): List<LocalTestCollection> =
            emptyList()

        override fun getDbSyncCollections(serviceId: Long): List<Collection> =
            emptyList()

        override fun create(provider: ContentProviderClient, remoteCollection: Collection): LocalTestCollection =
            LocalTestCollection(remoteCollection.url.toString())

        override fun syncCollection(
            provider: ContentProviderClient,
            localCollection: LocalTestCollection,
            remoteCollection: Collection
        ) {}

        override fun update(localCollection: LocalTestCollection, remoteCollection: Collection) {}

    }

}