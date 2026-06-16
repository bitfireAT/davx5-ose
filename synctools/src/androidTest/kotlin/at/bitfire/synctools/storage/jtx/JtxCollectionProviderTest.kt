/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.accounts.Account
import android.content.ContentProviderClient
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.synctools.storage.TaskProvider
import at.bitfire.synctools.test.GrantPermissionOrSkipRule
import at.techbee.jtx.JtxContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JtxCollectionProviderTest {

    @get:Rule
    val permissionRule = GrantPermissionOrSkipRule(TaskProvider.ProviderName.JtxBoard.permissions.toSet())

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val testAccount = Account(
        JtxCollectionProviderTest::class.java.name,
        JtxContract.JtxCollection.TEST_ACCOUNT_TYPE
    )

    private lateinit var client: ContentProviderClient
    private lateinit var provider: JtxCollectionProvider

    @Before
    fun setUp() {
        client = context.contentResolver.acquireContentProviderClient(JtxContract.AUTHORITY)!!
        provider = JtxCollectionProvider(testAccount, client)
    }

    @After
    fun tearDown() {
        provider.findCollections().forEach { collection ->
            collection.delete()
        }
        assertEquals(0, provider.findCollections().size)
        client.close()
    }

    private fun sampleValues(displayName: String = "Test Collection") = contentValuesOf(
        JtxContract.JtxCollection.URL to "https://example.com/test",
        JtxContract.JtxCollection.DISPLAYNAME to displayName,
        JtxContract.JtxCollection.SUPPORTSVTODO to true,
        JtxContract.JtxCollection.SUPPORTSVJOURNAL to true
    )


    @Test
    fun testCreateAndGetCollection() {
        val collection = provider.createAndGetCollection(sampleValues())

        assertEquals(testAccount.name, collection.values.getAsString(JtxContract.JtxCollection.ACCOUNT_NAME))
        assertEquals(testAccount.type, collection.values.getAsString(JtxContract.JtxCollection.ACCOUNT_TYPE))
        assertEquals("https://example.com/test", collection.url)
        assertEquals("Test Collection", collection.displayName)

        // clean up
        assertEquals(1, collection.delete())
    }

    @Test
    fun testFindCollections_empty() {
        assertEquals(0, provider.findCollections().size)
    }

    @Test
    fun testFindCollections_multiple() {
        provider.createCollection(sampleValues("Collection A"))
        provider.createCollection(sampleValues("Collection B"))

        val collections = provider.findCollections()
        assertEquals(2, collections.size)
    }

    @Test
    fun testFindCollections_withWhere() {
        provider.createCollection(sampleValues("Collection A"))
        provider.createCollection(sampleValues("Collection B"))

        val collections = provider.findCollections(
            where = "${JtxContract.JtxCollection.DISPLAYNAME} = ?",
            whereArgs = arrayOf("Collection A")
        )
        assertEquals(1, collections.size)
        assertEquals("Collection A", collections[0].displayName)
    }

    @Test
    fun testFindFirstCollection_found() {
        provider.createCollection(sampleValues())

        val collection = provider.findFirstCollection()
        assertNotNull(collection)
        assertEquals("Test Collection", collection!!.displayName)
    }

    @Test
    fun testFindFirstCollection_notFound() {
        val collection = provider.findFirstCollection()
        assertNull(collection)
    }

    @Test
    fun testGetCollection_found() {
        val id = provider.createCollection(sampleValues())

        val collection = provider.getCollection(id)
        assertNotNull(collection)
        assertEquals(id, collection!!.id)
        assertEquals("Test Collection", collection.displayName)
    }

    @Test
    fun testGetCollection_notFound() {
        val collection = provider.getCollection(Long.MAX_VALUE)
        assertNull(collection)
    }

    @Test
    fun testUpdateCollection() {
        val id = provider.createCollection(sampleValues())

        val updatedRows = provider.updateCollection(id, contentValuesOf(
            JtxContract.JtxCollection.DISPLAYNAME to "Updated Name"
        ))
        assertEquals(1, updatedRows)

        val updated = provider.getCollection(id)
        assertEquals("Updated Name", updated?.displayName)
    }

    @Test
    fun testDeleteCollection() {
        val id = provider.createCollection(sampleValues())
        assertEquals(1, provider.findCollections().size)

        val deletedRows = provider.deleteCollection(id)
        assertEquals(1, deletedRows)
        assertEquals(0, provider.findCollections().size)
    }

    @Test
    fun testDeleteCollection_viaCollectionObject() {
        val collection = provider.createAndGetCollection(sampleValues())
        assertEquals(1, collection.delete())
        assertEquals(0, provider.findCollections().size)
    }

    @Test
    fun testReadWriteCollectionSyncState_null() {
        val id = provider.createCollection(sampleValues())

        provider.writeCollectionSyncState(id, null)
        assertNull(provider.readCollectionSyncState(id))
    }

    @Test
    fun testReadWriteCollectionSyncState_value() {
        val id = provider.createCollection(sampleValues())

        provider.writeCollectionSyncState(id, "ctag-12345")
        assertEquals("ctag-12345", provider.readCollectionSyncState(id))
    }

    @Test
    fun testReadWriteCollectionSyncState_overwrite() {
        val id = provider.createCollection(sampleValues())

        provider.writeCollectionSyncState(id, "ctag-first")
        provider.writeCollectionSyncState(id, "ctag-second")
        assertEquals("ctag-second", provider.readCollectionSyncState(id))
    }

}
