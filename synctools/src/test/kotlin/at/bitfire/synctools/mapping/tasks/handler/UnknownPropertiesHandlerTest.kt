/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.UNKNOWN_PROPERTY_DATA
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnknownPropertiesHandlerTest {

    private val handler = UnknownPropertiesHandler()

    @Test
    fun `No unknown properties`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertTrue(task.unknownProperties.isEmpty())
    }

    @Test
    fun `Single unknown property`() {
        val task = Task()
        handler.process(contentValuesOf(
            UNKNOWN_PROPERTY_DATA to "[\"UID\", \"test-property\", {}]"
        ), task)

        assertEquals(1, task.unknownProperties.size)
        val prop = task.unknownProperties.first()
        assertEquals(Property.UID, prop.name)
        assertEquals("test-property", prop.value)
    }

    @Test
    fun `Unknown property with parameters`() {
        val task = Task()
        handler.process(contentValuesOf(
            UNKNOWN_PROPERTY_DATA to "[\"UID\", \"prop-value\", {\"X-CUSTOM\": \"custom-value\"}]"
        ), task)

        assertEquals(1, task.unknownProperties.size)
        val prop = task.unknownProperties.first()
        assertEquals(Property.UID, prop.name)
        assertEquals("prop-value", prop.value)
        assertEquals("custom-value", prop.getRequiredParameter<Parameter>("X-CUSTOM").value)
    }

    @Test
    fun `Multiple unknown properties accumulate`() {
        val task = Task()
        handler.process(contentValuesOf(
            UNKNOWN_PROPERTY_DATA to "[\"X-PROP1\", \"value1\", {}]"
        ), task)
        handler.process(contentValuesOf(
            UNKNOWN_PROPERTY_DATA to "[\"X-PROP2\", \"value2\", {}]"
        ), task)

        assertEquals(2, task.unknownProperties.size)
    }

    @Test
    fun `Null unknown property data is skipped`() {
        val task = Task()
        handler.process(ContentValues().apply {
            putNull(UNKNOWN_PROPERTY_DATA)
        }, task)
        assertTrue(task.unknownProperties.isEmpty())
    }

    @Test
    fun `Unknown property with invalid JSON`() {
        val task = Task()
        handler.process(contentValuesOf(
            UNKNOWN_PROPERTY_DATA to "not json"
        ), task)

        assertTrue(task.unknownProperties.isEmpty())
    }

}
