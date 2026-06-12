/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.TaskProvider
import at.bitfire.synctools.test.GrantPermissionOrSkipRule
import at.bitfire.synctools.test.assertJtxObjectAndExceptionsEqual
import at.bitfire.synctools.test.withJtxId
import at.bitfire.synctools.verifyCompat
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject.Component
import io.mockk.junit4.MockKRule
import io.mockk.spyk
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class JtxRecurringCollectionTest {

    companion object {

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionOrSkipRule(TaskProvider.PERMISSIONS_JTX.toSet())

        private val testAccount = Account(
            JtxRecurringCollectionTest::class.java.name,
            JtxContract.JtxCollection.TEST_ACCOUNT_TYPE
        )

        private lateinit var client: ContentProviderClient
        private lateinit var provider: JtxCollectionProvider
        private lateinit var collection: JtxCollection
        private lateinit var recurringCollection: JtxRecurringCollection

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            client = context.contentResolver.acquireContentProviderClient(JtxContract.AUTHORITY)!!
            provider = JtxCollectionProvider(testAccount, client)
            collection = provider.createAndGetCollection(contentValuesOf(
                JtxContract.JtxCollection.URL to "https://example.com/recurring-test",
                JtxContract.JtxCollection.DISPLAYNAME to "Recurring Test Collection",
                JtxContract.JtxCollection.SUPPORTSVTODO to true,
                JtxContract.JtxCollection.SUPPORTSVJOURNAL to true
            ))
            recurringCollection = spyk(JtxRecurringCollection(collection))
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            collection.delete()
            client.close()
        }

    }

    @get:Rule
    val mockkRule = MockKRule(this)

    @After
    fun cleanUp() {
        collection.deleteAllJtxObjects()
    }


    // test CRUD

    @Test
    fun testAddJtxObjectAndExceptions_and_GetById() {
        val (mainId, obj) = insertRecurring()
        val addedWithId = obj.withJtxId(mainId)

        // verify that cleanUp was called
        verifyCompat(exactly = 1) {
            recurringCollection.cleanUp(obj)
        }

        // verify stored data
        val result = recurringCollection.getById(mainId)
        assertJtxObjectAndExceptionsEqual(addedWithId, result!!, onlyFieldsInExpected = true)
    }

    @Test
    fun testFindJtxObjectAndExceptions() {
        val uid = "testFindJtxObjectAndExceptions"
        val (mainId, obj) = insertRecurring(uid = uid)
        val addedWithId = obj.withJtxId(mainId)

        val result = recurringCollection.findJtxObjectAndExceptions(
            "${JtxContract.JtxICalObject.UID}=?",
            arrayOf(uid)
        )
        assertJtxObjectAndExceptionsEqual(addedWithId, result!!, onlyFieldsInExpected = true)
    }

    @Test
    fun testFindJtxObjectAndExceptions_NotFound() {
        assertNull(
            recurringCollection.findJtxObjectAndExceptions(
                "${JtxContract.JtxICalObject.UID}=?",
                arrayOf("does-not-exist")
            )
        )
    }

    @Test
    fun testGetById_NotFound() {
        recurringCollection.deleteJtxObjectAndExceptions(Long.MAX_VALUE)
        assertNull(recurringCollection.getById(Long.MAX_VALUE))
    }

    @Test
    fun testGetById_withExceptionId_returnsNull() {
        val uid = "testGetById_withExceptionId"
        val (mainId, _) = insertRecurring(uid = uid)

        // find the exception row and call getById with its ID
        val exceptionId = collection.findJtxObjects(
            "${JtxContract.JtxICalObject.UID}=? AND ${JtxContract.JtxICalObject.RECURID} IS NOT NULL AND ${JtxContract.JtxICalObject.SEQUENCE} > 0",
            arrayOf(uid)
        ).first().entityValues.getAsLong(JtxContract.JtxICalObject.ID)

        assertNull("getById must return null when called with an exception ID", recurringCollection.getById(exceptionId))

        // the main object must still be accessible
        val main = recurringCollection.getById(mainId)
        assertEquals(1, main!!.exceptions.size)
    }

    @Test
    fun testUpdateJtxObjectAndExceptions_withExceptionId_throws() {
        val uid = "testUpdateJtxObjectAndExceptions_withExceptionId"
        insertRecurring(uid = uid)

        val exceptionId = collection.findJtxObjects(
            "${JtxContract.JtxICalObject.UID}=? AND ${JtxContract.JtxICalObject.RECURID} IS NOT NULL AND ${JtxContract.JtxICalObject.SEQUENCE} > 0",
            arrayOf(uid)
        ).first().entityValues.getAsLong(JtxContract.JtxICalObject.ID)

        try {
            recurringCollection.updateJtxObjectAndExceptions(exceptionId, JtxObjectAndExceptions(
                main = Entity(contentValuesOf(
                    JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
                    JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
                    JtxContract.JtxICalObject.UID to uid,
                    JtxContract.JtxICalObject.SUMMARY to "Should not work"
                )),
                exceptions = emptyList()
            ))
            fail("updateJtxObjectAndExceptions must throw when called with an exception ID")
        } catch (_: LocalStorageException) {
            // expected
        }
    }

    @Test
    fun testIterateJtxObjectAndExceptions() {
        val uid1 = "testIterateJtxObjectAndExceptions1"
        val uid2 = "testIterateJtxObjectAndExceptions2"
        val (id1, obj1) = insertRecurring(uid = uid1)
        val (id2, obj2) = insertRecurring(uid = uid2)

        val result = mutableListOf<JtxObjectAndExceptions>()
        recurringCollection.iterateJtxObjectAndExceptions(
            "${JtxContract.JtxICalObject.UID} IN (?, ?)",
            arrayOf(uid1, uid2)
        ) { result += it }

        val orderedResult = result.sortedBy { it.main.entityValues.getAsLong(JtxContract.JtxICalObject.ID) }
        assertEquals(2, orderedResult.size)
        assertJtxObjectAndExceptionsEqual(obj1.withJtxId(id1), orderedResult[0], onlyFieldsInExpected = true)
        assertJtxObjectAndExceptionsEqual(obj2.withJtxId(id2), orderedResult[1], onlyFieldsInExpected = true)
    }

    @Test
    fun testIterateJtxObjectAndExceptions_NotFound() {
        recurringCollection.iterateJtxObjectAndExceptions(
            "${JtxContract.JtxICalObject.UID}=?",
            arrayOf("does-not-exist")
        ) {
            fail("body must not be called, when does not exist")
        }
    }

    @Test
    fun testUpdateJtxObjectAndExceptions() {
        val now = 1754233504000L     // Sun Aug 03 2025 15:05:04 GMT+0000
        val uid = "testUpdateJtxObjectAndExceptions"
        val initialMain = Entity(contentValuesOf(
            JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
            JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
            JtxContract.JtxICalObject.UID to uid,
            JtxContract.JtxICalObject.SUMMARY to "Initial Main",
            JtxContract.JtxICalObject.DTSTART to now,
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=3"
        ))
        val initialException = Entity(contentValuesOf(
            JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
            JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
            JtxContract.JtxICalObject.UID to uid,
            JtxContract.JtxICalObject.SUMMARY to "Initial Exception",
            JtxContract.JtxICalObject.DTSTART to now + 86400000,
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.RECURID to "20250804T150504Z",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC"
        ))
        val addedId = recurringCollection.addJtxObjectAndExceptions(
            JtxObjectAndExceptions(main = initialMain, exceptions = listOf(initialException))
        )

        val updatedMain = Entity(contentValuesOf(
            JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
            JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
            JtxContract.JtxICalObject.UID to uid,
            JtxContract.JtxICalObject.SUMMARY to "Updated Main",
            JtxContract.JtxICalObject.DTSTART to now,
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=3"
        ))
        val updatedException = Entity(contentValuesOf(
            JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
            JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
            JtxContract.JtxICalObject.UID to uid,
            JtxContract.JtxICalObject.SUMMARY to "Updated Exception",
            JtxContract.JtxICalObject.DTSTART to now + 86400000,
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.RECURID to "20250804T150504Z",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC"
        ))
        val updatedObj = JtxObjectAndExceptions(main = updatedMain, exceptions = listOf(updatedException))

        val updatedId = recurringCollection.updateJtxObjectAndExceptions(addedId, updatedObj)
        assertEquals(addedId, updatedId)

        // verify that cleanUp was called (once for add, once for update)
        verifyCompat(exactly = 1) {
            recurringCollection.cleanUp(updatedObj)
        }

        val result = recurringCollection.getById(addedId)
        assertJtxObjectAndExceptionsEqual(updatedObj.withJtxId(addedId), result!!, onlyFieldsInExpected = true)
    }

    @Test
    fun testDeleteJtxObjectAndExceptions_batch() {
        val now = 1754233504000L
        val uid = "testDeleteJtxObjectAndExceptions_batch"
        val mainId = recurringCollection.addJtxObjectAndExceptions(
            JtxObjectAndExceptions(
                main = Entity(
                    contentValuesOf(
                        JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
                        JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
                        JtxContract.JtxICalObject.UID to uid,
                        JtxContract.JtxICalObject.SUMMARY to "Main",
                        JtxContract.JtxICalObject.DTSTART to now,
                        JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                        JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=3"
                    )
                ),
                exceptions = listOf(
                    Entity(
                        contentValuesOf(
                            JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
                            JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
                            JtxContract.JtxICalObject.UID to uid,
                            JtxContract.JtxICalObject.SUMMARY to "Exception",
                            JtxContract.JtxICalObject.DTSTART to now + 86400000,
                            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                            JtxContract.JtxICalObject.RECURID to "20250804T150504Z",
                            JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC"
                        )
                    )
                )
            )
        )

        val batch = JtxBatchOperation(collection.client)
        recurringCollection.deleteJtxObjectAndExceptions(mainId, batch)
        batch.commit()

        assertNull(recurringCollection.getById(mainId))
        assertEquals(0, collection.countJtxObjects("${JtxContract.JtxICalObject.UID}=?", arrayOf(uid)))
    }

    @Test
    fun testDeleteJtxObjectAndExceptions() {
        val now = 1754233504000L
        val uid = "testDeleteJtxObjectAndExceptions"
        val mainId = recurringCollection.addJtxObjectAndExceptions(JtxObjectAndExceptions(
            main = Entity(contentValuesOf(
                JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
                JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
                JtxContract.JtxICalObject.UID to uid,
                JtxContract.JtxICalObject.SUMMARY to "Main",
                JtxContract.JtxICalObject.DTSTART to now,
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=3"
            )),
            exceptions = listOf(
                Entity(contentValuesOf(
                    JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
                    JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
                    JtxContract.JtxICalObject.UID to uid,
                    JtxContract.JtxICalObject.SUMMARY to "Exception",
                    JtxContract.JtxICalObject.DTSTART to now + 86400000,
                    JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                    JtxContract.JtxICalObject.RECURID to "20250804T150504Z",
                    JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC"
                ))
            )
        ))

        recurringCollection.deleteJtxObjectAndExceptions(mainId)

        assertNull(recurringCollection.getById(mainId))
        // verify exceptions are also gone
        assertEquals(0, collection.countJtxObjects(
            "${JtxContract.JtxICalObject.UID}=?", arrayOf(uid)
        ))
    }


    @Test
    fun testFindExceptionRow() {
        val uid = "testFindExceptionRow"
        insertRecurring(uid = uid)

        val result = recurringCollection.findExceptionRow(uid, "20250804T150504Z")
        assertNotNull(result)
        assertEquals(uid, result!!.getAsString(JtxContract.JtxICalObject.UID))
        assertEquals("20250804T150504Z", result.getAsString(JtxContract.JtxICalObject.RECURID))
    }

    @Test
    fun testFindExceptionRow_NotFound() {
        assertNull(recurringCollection.findExceptionRow("does-not-exist", "20250804T150504Z"))
    }


    // test validation / clean-up logic

    @Test
    fun testCleanUp_Recurring_Exceptions_NoUid() {
        val cleaned = recurringCollection.cleanUp(JtxObjectAndExceptions(
            main = Entity(contentValuesOf(
                JtxContract.JtxICalObject.SUMMARY to "Recurring Main",
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY"
                // no UID
            )),
            exceptions = listOf(
                Entity(contentValuesOf(JtxContract.JtxICalObject.SUMMARY to "Exception"))
            )
        ))

        // exceptions must be dropped because UID is not set
        assertTrue(cleaned.exceptions.isEmpty())
    }

    @Test
    fun testCleanUp_Recurring_Exceptions_WithUid() {
        val uid = "testCleanUp-uid"
        val original = JtxObjectAndExceptions(
            main = Entity(contentValuesOf(
                JtxContract.JtxICalObject.UID to uid,
                JtxContract.JtxICalObject.SUMMARY to "Recurring Main",
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY"
            )),
            exceptions = listOf(
                Entity(contentValuesOf(
                    JtxContract.JtxICalObject.UID to uid,
                    JtxContract.JtxICalObject.SUMMARY to "Exception",
                    JtxContract.JtxICalObject.RECURID to "20250804T150504Z"
                ))
            )
        )
        val cleaned = recurringCollection.cleanUp(original)

        // exceptions must be retained (recurring + UID present)
        assertEquals(1, cleaned.exceptions.size)
    }

    @Test
    fun testCleanUp_NotRecurring_Exceptions() {
        val cleaned = recurringCollection.cleanUp(JtxObjectAndExceptions(
            main = Entity(contentValuesOf(
                JtxContract.JtxICalObject.UID to "some-uid",
                JtxContract.JtxICalObject.SUMMARY to "Non-Recurring Main"
                // no RRULE or RDATE
            )),
            exceptions = listOf(
                Entity(contentValuesOf(JtxContract.JtxICalObject.SUMMARY to "Exception"))
            )
        ))

        // exceptions must be dropped because main is not recurring
        assertTrue(cleaned.exceptions.isEmpty())
    }

    @Test
    fun testCleanMainObject_RemovesRecurIdFields() {
        val result = recurringCollection.cleanMainObject(Entity(contentValuesOf(
            JtxContract.JtxICalObject.SUMMARY to "Main",
            JtxContract.JtxICalObject.RECURID to "20250804T150504Z",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC"
        )))

        // RECURID and RECURID_TIMEZONE must be removed; only SUMMARY remains
        assertEquals(1, result.entityValues.size())
        assertEquals("Main", result.entityValues.getAsString(JtxContract.JtxICalObject.SUMMARY))
    }

    @Test
    fun testCleanException_RemovesRecurrenceFields_SetsUid() {
        val result = recurringCollection.cleanException(Entity(contentValuesOf(
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY",
            JtxContract.JtxICalObject.RDATE to "20250804T150504Z",
            JtxContract.JtxICalObject.EXDATE to "20250805T150504Z"
        )), "target-uid")

        // all recurrence fields removed; UID set to the given value
        assertEquals(1, result.entityValues.size())
        assertEquals("target-uid", result.entityValues.getAsString(JtxContract.JtxICalObject.UID))
    }


    // test processing dirty/deleted exceptions

    @Test
    fun testProcessDeletedExceptions() {
        val now = System.currentTimeMillis()
        val uid = "testProcessDeletedExceptions"
        val mainValues = contentValuesOf(
            JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
            JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
            JtxContract.JtxICalObject.UID to uid,
            JtxContract.JtxICalObject.SUMMARY to "Main",
            JtxContract.JtxICalObject.DTSTART to now,
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.DIRTY to 0,
            JtxContract.JtxICalObject.DELETED to 0,
            JtxContract.JtxICalObject.SEQUENCE to 15
        )
        val exNotDeleted = Entity(contentValuesOf(
            JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
            JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
            JtxContract.JtxICalObject.UID to uid,
            JtxContract.JtxICalObject.SUMMARY to "Not deleted exception",
            JtxContract.JtxICalObject.DTSTART to now + 86400000,
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.RECURID to "20250804T150504Z",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.DIRTY to 0,
            JtxContract.JtxICalObject.DELETED to 0
        ))
        val mainId = recurringCollection.addJtxObjectAndExceptions(
            JtxObjectAndExceptions(
                main = Entity(mainValues),
                exceptions = listOf(
                    exNotDeleted,
                    Entity(contentValuesOf(
                        JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
                        JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
                        JtxContract.JtxICalObject.UID to uid,
                        JtxContract.JtxICalObject.SUMMARY to "Deleted exception",
                        JtxContract.JtxICalObject.DTSTART to now + 2 * 86400000,
                        JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                        JtxContract.JtxICalObject.RECURID to "20250805T150504Z",
                        JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC",
                        JtxContract.JtxICalObject.DIRTY to 1,
                        JtxContract.JtxICalObject.DELETED to 1
                    ))
                )
            )
        )

        // should update main object and purge the deleted exception
        recurringCollection.processDeletedExceptions()

        val result = recurringCollection.getById(mainId)!!
        assertJtxObjectAndExceptionsEqual(
            JtxObjectAndExceptions(
                main = Entity(ContentValues(mainValues).apply {
                    put(JtxContract.JtxICalObject.DIRTY, 1)
                    put(JtxContract.JtxICalObject.SEQUENCE, 16)
                }),
                exceptions = listOf(exNotDeleted)
            ), result, onlyFieldsInExpected = true
        )
    }

    @Test
    fun testProcessDirtyExceptions() {
        val now = System.currentTimeMillis()
        val uid = "testProcessDirtyExceptions"
        val mainValues = contentValuesOf(
            JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
            JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
            JtxContract.JtxICalObject.UID to uid,
            JtxContract.JtxICalObject.SUMMARY to "Main",
            JtxContract.JtxICalObject.DTSTART to now,
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.DIRTY to 0,
            JtxContract.JtxICalObject.DELETED to 0,
            JtxContract.JtxICalObject.SEQUENCE to 15
        )
        val exDirtyValues = contentValuesOf(
            JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
            JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
            JtxContract.JtxICalObject.UID to uid,
            JtxContract.JtxICalObject.SUMMARY to "Dirty exception",
            JtxContract.JtxICalObject.DTSTART to now + 86400000,
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.RECURID to "20250804T150504Z",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.DIRTY to 1,
            JtxContract.JtxICalObject.DELETED to 0,
            JtxContract.JtxICalObject.SEQUENCE to null
        )
        val mainId = recurringCollection.addJtxObjectAndExceptions(
            JtxObjectAndExceptions(
                main = Entity(mainValues),
                exceptions = listOf(Entity(exDirtyValues))
            )
        )

        // should mark main as dirty and increase exception SEQUENCE
        recurringCollection.processDirtyExceptions()

        val result = recurringCollection.getById(mainId)!!
        assertJtxObjectAndExceptionsEqual(
            JtxObjectAndExceptions(
                main = Entity(ContentValues(mainValues).apply {
                    put(JtxContract.JtxICalObject.DIRTY, 1)
                }),
                exceptions = listOf(Entity(ContentValues(exDirtyValues).apply {
                    put(JtxContract.JtxICalObject.DIRTY, 0)
                    put(JtxContract.JtxICalObject.SEQUENCE, 2)
                }))
            ), result, onlyFieldsInExpected = true
        )
    }


    // helpers

    private fun insertRecurring(uid: String = UUID.randomUUID().toString()): Pair<Long, JtxObjectAndExceptions> {
        val now = 1754233504000L     // Sun Aug 03 2025 15:05:04 GMT+0000
        val obj = JtxObjectAndExceptions(
            main = Entity(contentValuesOf(
                JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
                JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
                JtxContract.JtxICalObject.UID to uid,
                JtxContract.JtxICalObject.SUMMARY to "Main Task",
                JtxContract.JtxICalObject.DTSTART to now,
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=3"
            )),
            exceptions = listOf(
                Entity(contentValuesOf(
                    JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collection.id,
                    JtxContract.JtxICalObject.COMPONENT to Component.VTODO.name,
                    JtxContract.JtxICalObject.UID to uid,
                    JtxContract.JtxICalObject.SUMMARY to "Exception Task",
                    JtxContract.JtxICalObject.DTSTART to now + 86400000,
                    JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                    JtxContract.JtxICalObject.RECURID to "20250804T150504Z",
                    JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC"
                ))
            )
        )
        val id = recurringCollection.addJtxObjectAndExceptions(obj)
        return id to obj
    }

}
