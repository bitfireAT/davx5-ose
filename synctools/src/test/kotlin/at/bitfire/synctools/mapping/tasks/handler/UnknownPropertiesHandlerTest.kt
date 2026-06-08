/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.storage.tasks.DmfsTasksContract.UNKNOWN_PROPERTY_DATA
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import org.dmfs.tasks.contract.TaskContract.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnknownPropertiesHandlerTest {

    private val handler = UnknownPropertiesHandler()

    @Test
    fun `legacy No unknown properties`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertTrue(task.unknownProperties.isEmpty())
    }

    @Test
    fun `legacy Single unknown property`() {
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
    fun `legacy Unknown property with parameters`() {
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
    fun `legacy Multiple unknown properties accumulate`() {
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
    fun `legacy Null unknown property data is skipped`() {
        val task = Task()
        handler.process(ContentValues().apply {
            putNull(UNKNOWN_PROPERTY_DATA)
        }, task)
        assertTrue(task.unknownProperties.isEmpty())
    }

    @Test
    fun `legacy Unknown property with invalid JSON`() {
        val task = Task()
        handler.process(contentValuesOf(
            UNKNOWN_PROPERTY_DATA to "not json"
        ), task)

        assertTrue(task.unknownProperties.isEmpty())
    }

    @Test
    fun `No unknown properties`() {
        val input = Entity(ContentValues())
        val task = VToDo()
        val initialPropertyCount = task.propertyList.all.size

        handler.process(from = input, main = input, to = task)

        assertEquals(initialPropertyCount, task.propertyList.all.size)
    }

    @Test
    fun `Single unknown property`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Properties.MIMETYPE to UnknownProperty.CONTENT_ITEM_TYPE,
                UNKNOWN_PROPERTY_DATA to "[\"X-CUSTOM-PROP\", \"test-property\", {}]"
            ))
        }
        val task = VToDo()
        val initialPropertyCount = task.propertyList.all.size

        handler.process(from = input, main = input, to = task)

        assertEquals(initialPropertyCount + 1, task.propertyList.all.size)
        val prop = task.propertyList.all.find { it.name == "X-CUSTOM-PROP" }!!
        assertEquals("test-property", prop.value)
    }

    @Test
    fun `Unknown property with parameters`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Properties.MIMETYPE to UnknownProperty.CONTENT_ITEM_TYPE,
                UNKNOWN_PROPERTY_DATA to "[\"X-CUSTOM-PROP\", \"prop-value\", {\"X-CUSTOM\": \"custom-value\"}]"
            ))
        }
        val task = VToDo()
        val initialPropertyCount = task.propertyList.all.size

        handler.process(from = input, main = input, to = task)

        assertEquals(initialPropertyCount + 1, task.propertyList.all.size)
        val prop = task.propertyList.all.find { it.name == "X-CUSTOM-PROP" }!!
        assertEquals("prop-value", prop.value)
        assertEquals("custom-value", prop.getRequiredParameter<Parameter>("X-CUSTOM").value)
    }

    @Test
    fun `Multiple unknown properties accumulate`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Properties.MIMETYPE to UnknownProperty.CONTENT_ITEM_TYPE,
                UNKNOWN_PROPERTY_DATA to "[\"X-PROP1\", \"value1\", {}]"
            ))
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Properties.MIMETYPE to UnknownProperty.CONTENT_ITEM_TYPE,
                UNKNOWN_PROPERTY_DATA to "[\"X-PROP2\", \"value2\", {}]"
            ))
        }
        val task = VToDo()
        val initialPropertyCount = task.propertyList.all.size

        handler.process(from = input, main = input, to = task)

        assertEquals(initialPropertyCount + 2, task.propertyList.all.size)
    }

    @Test
    fun `Null unknown property data is skipped`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Properties.MIMETYPE to UnknownProperty.CONTENT_ITEM_TYPE
            ).apply {
                putNull(UNKNOWN_PROPERTY_DATA)
            })
        }
        val task = VToDo()
        val initialPropertyCount = task.propertyList.all.size

        handler.process(from = input, main = input, to = task)

        assertEquals(initialPropertyCount, task.propertyList.all.size)
    }

    @Test
    fun `Unknown property with invalid JSON`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Properties.MIMETYPE to UnknownProperty.CONTENT_ITEM_TYPE,
                UNKNOWN_PROPERTY_DATA to "not json"
            ))
        }
        val task = VToDo()
        val initialPropertyCount = task.propertyList.all.size

        handler.process(from = input, main = input, to = task)

        assertEquals(initialPropertyCount, task.propertyList.all.size)
    }

}
