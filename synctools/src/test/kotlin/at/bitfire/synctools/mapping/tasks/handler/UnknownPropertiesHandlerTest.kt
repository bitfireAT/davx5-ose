/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.UnknownProperty
import at.bitfire.synctools.storage.tasks.DmfsTasksContract.UNKNOWN_PROPERTY_DATA
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.component.VToDo
import org.dmfs.tasks.contract.TaskContract.Properties
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnknownPropertiesHandlerTest {

    private val handler = UnknownPropertiesHandler()


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
