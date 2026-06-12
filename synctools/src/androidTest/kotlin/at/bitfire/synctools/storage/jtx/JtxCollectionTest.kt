/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.TaskProvider
import at.bitfire.synctools.test.GrantPermissionOrSkipRule
import at.bitfire.synctools.test.assertEntitiesEqual
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject.Component
import at.techbee.jtx.JtxContract.asSyncAdapter
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class JtxCollectionTest {

    companion object {

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionOrSkipRule(TaskProvider.ProviderName.JtxBoard.permissions.toSet())

        private val testAccount = Account(
            JtxCollectionTest::class.java.name,
            JtxContract.JtxCollection.TEST_ACCOUNT_TYPE
        )

        private lateinit var client: ContentProviderClient
        private lateinit var provider: JtxCollectionProvider
        private lateinit var collection: JtxCollection

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            client = context.contentResolver.acquireContentProviderClient(JtxContract.AUTHORITY)!!
            provider = JtxCollectionProvider(testAccount, client)
            collection = provider.createAndGetCollection(contentValuesOf(
                JtxContract.JtxCollection.URL to "https://example.com/test",
                JtxContract.JtxCollection.DISPLAYNAME to "Test Collection",
                JtxContract.JtxCollection.SUPPORTSVTODO to true,
                JtxContract.JtxCollection.SUPPORTSVJOURNAL to true
            ))
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            collection.delete()
            client.close()
        }

    }

    @After
    fun cleanUp() {
        collection.deleteAllJtxObjects()
    }


    // test helpers

    private fun sampleEntity(summary: String = "Test Object") = Entity(contentValuesOf(
        JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
        JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
        JtxContract.JtxICalObject.SUMMARY to summary
    ))

    private fun sampleEntityWithSubValues(summary: String = "Test Object") =
        sampleEntity(summary).apply {
            addSubValue(JtxContract.JtxCategory.CONTENT_URI, contentValuesOf(
                JtxContract.JtxCategory.TEXT to "sample-category"
            ))
        }


    // CRUD JtxObject

    @Test
    fun testAddJtxObject_and_GetJtxObject() {
        val entity = sampleEntityWithSubValues()
        val id = collection.addJtxObject(entity)

        val result = collection.getJtxObject(id)!!
        assertEntitiesEqual(entity, result, onlyFieldsInExpected = true)
    }

    @Test
    fun testAddJtxObject_toBatch_AsSecondOperation() {
        val batch = JtxBatchOperation(client)

        // first operation: no-op
        batch += BatchOperation.CpoBuilder
            .newUpdate(collection.jtxObjectsUri)
            .withValue(JtxContract.JtxICalObject.UID, "won't happen")
            .withSelection(
                "${JtxContract.JtxICalObject.UID}=?",
                arrayOf("testAddObject_toBatch_AsSecondOperation")
            )

        // second operation (object row index > 0)
        val entity = sampleEntityWithSubValues()
        val idx = batch.nextBackrefIdx()
        collection.addJtxObject(entity, batch)

        batch.commit()
        val id = ContentUris.parseId(batch.getResult(idx)!!.uri!!)

        val result = collection.getJtxObject(id)!!
        assertEntitiesEqual(entity, result, onlyFieldsInExpected = true)
    }

    @Test
    fun testCountJtxObjects_empty() {
        assertEquals(0, collection.countJtxObjects(null, null))
    }

    @Test
    fun testCountJtxObjects_withJtxObjects() {
        collection.addJtxObject(sampleEntity("Object 1"))
        collection.addJtxObject(sampleEntity("Object 2"))
        assertEquals(2, collection.countJtxObjects(null, null))
    }

    @Test
    fun testCountJtxObjects_filterMatch() {
        collection.addJtxObject(sampleEntity("Object A"))
        collection.addJtxObject(sampleEntity("Object B"))
        assertEquals(
            1,
            collection.countJtxObjects(
                "${JtxContract.JtxICalObject.SUMMARY}=?",
                arrayOf("Object A")
            )
        )
    }

    @Test
    fun testCountJtxObjects_filterNoMatch() {
        collection.addJtxObject(sampleEntity("Object A"))
        assertEquals(
            0,
            collection.countJtxObjects(
                "${JtxContract.JtxICalObject.SUMMARY}=?",
                arrayOf("Does Not Exist")
            )
        )
    }

    @Test
    fun testFindJtxObject_notFound() {
        assertNull(
            collection.findJtxObject(
                "${JtxContract.JtxICalObject.SUMMARY}=?",
                arrayOf("Does Not Exist")
            )
        )
    }

    @Test
    fun testFindJtxObject_found() {
        val entity = sampleEntity("Test Object")
        collection.addJtxObject(entity)

        val result = collection.findJtxObject(
            "${JtxContract.JtxICalObject.SUMMARY}=?",
            arrayOf("Test Object")
        )
        assertNotNull(result)
        assertEntitiesEqual(entity, result!!, onlyFieldsInExpected = true)
    }

    @Test
    fun testFindJtxObjects() {
        collection.addJtxObject(sampleEntity("Object A"))
        val id2 = collection.addJtxObject(sampleEntity("Object B"))
        val id3 = collection.addJtxObject(sampleEntity("Object B"))

        val results = collection.findJtxObjects(
            "${JtxContract.JtxICalObject.SUMMARY}=?",
            arrayOf("Object B")
        )
        assertEquals(2, results.size)
        assertEquals(
            setOf(id2, id3),
            results.map { it.entityValues.getAsLong(JtxContract.JtxICalObject.ID) }.toSet()
        )
    }

    @Test
    fun testFindJtxObjectRow() {
        val id = collection.addJtxObject(sampleEntity("Row Object"))

        val result = collection.findJtxObjectRow(
            arrayOf(JtxContract.JtxICalObject.ID, JtxContract.JtxICalObject.SUMMARY),
            "${JtxContract.JtxICalObject.SUMMARY}=?",
            arrayOf("Row Object")
        )
        assertNotNull(result)
        assertEquals(id, result!!.getAsLong(JtxContract.JtxICalObject.ID))
        assertEquals("Row Object", result.getAsString(JtxContract.JtxICalObject.SUMMARY))
    }

    @Test
    fun testFindJtxObjectRow_NotExisting() {
        assertNull(
            collection.findJtxObjectRow(
                null,
                "${JtxContract.JtxICalObject.SUMMARY}=?",
                arrayOf("Does Not Exist")
            )
        )
    }

    @Test
    fun testGetJtxObjectRow() {
        val id = collection.addJtxObject(sampleEntity("Row Object by ID"))

        val result = collection.getJtxObjectRow(id, arrayOf(JtxContract.JtxICalObject.ID, JtxContract.JtxICalObject.SUMMARY))
        assertNotNull(result)
        assertEquals(id, result!!.getAsLong(JtxContract.JtxICalObject.ID))
        assertEquals("Row Object by ID", result.getAsString(JtxContract.JtxICalObject.SUMMARY))
    }

    @Test
    fun testGetJtxObjectRow_NotExisting() {
        assertNull(collection.getJtxObjectRow(Long.MAX_VALUE))
    }

    @Test
    fun testIterateJtxObjectRows() {
        val id1 = collection.addJtxObject(sampleEntity("Object 1"))
        val id2 = collection.addJtxObject(sampleEntity("Object 2"))

        val result = mutableListOf<ContentValues>()
        collection.iterateJtxObjectRows(
            arrayOf(JtxContract.JtxICalObject.ID, JtxContract.JtxICalObject.SUMMARY),
            null, null
        ) { row ->
            result += row
        }

        assertEquals(
            setOf(id1, id2),
            result.map { it.getAsLong(JtxContract.JtxICalObject.ID) }.toSet()
        )
        assertEquals(
            setOf("Object 1", "Object 2"),
            result.map { it.getAsString(JtxContract.JtxICalObject.SUMMARY) }.toSet()
        )
    }

    @Test
    fun testIterateJtxObjects() {
        val id1 = collection.addJtxObject(sampleEntityWithSubValues("Object 1"))
        val id2 = collection.addJtxObject(sampleEntityWithSubValues("Object 2"))

        val result = mutableListOf<Entity>()
        collection.iterateJtxObjects(null, null) { result += it }

        assertEquals(
            setOf(id1, id2),
            result.map { it.entityValues.getAsLong(JtxContract.JtxICalObject.ID) }.toSet()
        )
        assertEquals(
            setOf("Object 1", "Object 2"),
            result.map { it.entityValues.getAsString(JtxContract.JtxICalObject.SUMMARY) }.toSet()
        )
        // each entity should have its sub-rows loaded
        result.forEach { assertEquals(1, it.subValues.size) }
    }

    @Test
    fun testUpdateJtxObjectRow() {
        val id = collection.addJtxObject(sampleEntity("Original Title"))
        collection.updateJtxObjectRow(id, contentValuesOf(JtxContract.JtxICalObject.SUMMARY to "Updated Title"))
        assertEquals("Updated Title", collection.getJtxObject(id)!!.entityValues.getAsString(JtxContract.JtxICalObject.SUMMARY))
    }

    @Test
    fun testUpdateJtxObjectRowBatch() {
        val id = collection.addJtxObject(sampleEntity("Original Title"))

        val batch = JtxBatchOperation(client)
        collection.updateJtxObjectRow(id, contentValuesOf(JtxContract.JtxICalObject.SUMMARY to "Batch Updated Title"), batch)
        batch.commit()

        assertEquals("Batch Updated Title", collection.getJtxObject(id)!!.entityValues.getAsString(JtxContract.JtxICalObject.SUMMARY))
    }

    @Test
    fun testUpdateJtxObject() {
        val entity = sampleEntityWithSubValues("Original Title")
        val id = collection.addJtxObject(entity)

        // build updated entity: new summary, different category
        val updatedEntity = Entity(ContentValues(entity.entityValues).apply {
            put(JtxContract.JtxICalObject.SUMMARY, "New Title")
        }).apply {
            addSubValue(JtxContract.JtxCategory.CONTENT_URI, contentValuesOf(
                JtxContract.JtxCategory.TEXT to "updated-category"
            ))
        }
        collection.updateJtxObject(id, updatedEntity)

        val result = collection.getJtxObject(id)!!
        assertEntitiesEqual(updatedEntity, result, onlyFieldsInExpected = true)
    }

    @Test
    fun testUpdateJtxObject_viaBatch() {
        val entity = sampleEntityWithSubValues("Original Title")
        val id = collection.addJtxObject(entity)

        val updatedEntity = Entity(ContentValues(entity.entityValues).apply {
            put(JtxContract.JtxICalObject.SUMMARY, "Batch Updated Title")
        }).apply {
            addSubValue(JtxContract.JtxCategory.CONTENT_URI, contentValuesOf(
                JtxContract.JtxCategory.TEXT to "batch-updated-category"
            ))
        }

        val batch = JtxBatchOperation(client)
        collection.updateJtxObject(id, updatedEntity, batch)
        batch.commit()

        val result = collection.getJtxObject(id)!!
        assertEntitiesEqual(updatedEntity, result, onlyFieldsInExpected = true)
    }

    @Test
    fun testUpdateJtxObjectRows() {
        val id = collection.addJtxObject(sampleEntity("Original Title"))

        collection.updateJtxObjectRows(
            contentValuesOf(JtxContract.JtxICalObject.SUMMARY to "Bulk Updated"),
            "${JtxContract.JtxICalObject.SUMMARY}=?",
            arrayOf("Original Title")
        )

        assertEquals("Bulk Updated", collection.getJtxObject(id)!!.entityValues.getAsString(JtxContract.JtxICalObject.SUMMARY))
    }

    @Test
    fun testDeleteJtxObject() {
        val id = collection.addJtxObject(sampleEntity())
        assertNotNull(collection.getJtxObject(id))

        collection.deleteJtxObject(id)
        assertNull(collection.getJtxObject(id))
    }

    @Test
    fun testDeleteJtxObject_viaBatch() {
        val id = collection.addJtxObject(sampleEntity())
        assertNotNull(collection.getJtxObject(id))

        val batch = JtxBatchOperation(client)
        collection.deleteJtxObject(id, batch)
        batch.commit()

        assertNull(collection.getJtxObject(id))
    }

    @Test
    fun testDeleteJtxObject_cascadesSubRows() {
        val entity = sampleEntityWithSubValues()
        val id = collection.addJtxObject(entity)

        // verify sub-row was created
        val before = collection.getJtxObject(id)!!
        assertEquals(1, before.subValues.size)

        collection.deleteJtxObject(id)

        // object is gone
        assertNull(collection.getJtxObject(id))

        // verify category sub-row was cascade-deleted by the provider
        val remainingCategories = client.query(
                JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(testAccount),
                arrayOf(JtxContract.JtxCategory.ID),
                "${JtxContract.JtxCategory.ICALOBJECT_ID}=?",
                arrayOf(id.toString()),
                null
            )?.use { it.count } ?: 0
        assertEquals(0, remainingCategories)
    }

    @Test
    fun testCountJtxObjects_isolatedToCollection() {
        val otherCollection = provider.createAndGetCollection(contentValuesOf(
            JtxContract.JtxCollection.URL to "https://example.com/other",
            JtxContract.JtxCollection.DISPLAYNAME to "Other Collection",
            JtxContract.JtxCollection.SUPPORTSVTODO to true
        ))
        try {
            // object in other collection
            otherCollection.addJtxObject(Entity(contentValuesOf(
                JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to otherCollection.id,
                JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
                JtxContract.JtxICalObject.SUMMARY to "Other Object"
            )))

            // object in our collection
            collection.addJtxObject(sampleEntity())

            assertEquals(1, collection.countJtxObjects(null, null))
            assertEquals(1, otherCollection.countJtxObjects(null, null))
        } finally {
            otherCollection.delete()
        }
    }

}
