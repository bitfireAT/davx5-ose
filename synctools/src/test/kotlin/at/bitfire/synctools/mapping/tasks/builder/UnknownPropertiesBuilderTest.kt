/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.DmfsTasksContract.UNKNOWN_PROPERTY_DATA
import at.bitfire.synctools.test.assertContentValuesEqual
import io.mockk.every
import io.mockk.mockk
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.XProperty
import org.dmfs.tasks.contract.TaskContract.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.temporal.Temporal

@RunWith(RobolectricTestRunner::class)
class UnknownPropertiesBuilderTest {

    private val propertiesUri = Uri.parse("content://org.dmfs.tasks/properties")
    private val taskList = mockk<DmfsTaskList> {
        every { tasksPropertiesUri() } returns propertiesUri
    }
    private val builder = UnknownPropertiesBuilder(taskList)

    @Test
    fun `old No unknown properties`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `old Unknown property with value and parameters`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task().also {
                it.unknownProperties += (XProperty("X-Some-Property", "Some Value")
                    .add<XProperty>(XParameter("Param1", "Value1"))
                    .add<XProperty>(XParameter("Param2", "Value2")))
            },
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            Properties.MIMETYPE to UnknownProperty.CONTENT_ITEM_TYPE,
            UNKNOWN_PROPERTY_DATA to "[\"X-Some-Property\",\"Some Value\",{\"Param1\":\"Value1\",\"Param2\":\"Value2\"}]"
        ), result.subValues.first().values)
        assertEquals(propertiesUri, result.subValues.first().uri)
    }

    @Test
    fun `old Unknown property exceeding size limit is ignored`() {
        val result = Entity(ContentValues())
        val longValue = "x".repeat(UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE + 1)
        builder.build(
            from = Task().also {
                it.unknownProperties += XProperty("X-Huge-Property", longValue)
            },
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `No unknown properties`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `Unknown property with value and parameters`() {
        val result = Entity(ContentValues())
        val prop = XProperty("X-Some-Property", "Some Value")
            .add<XProperty>(XParameter("Param1", "Value1"))
            .add<XProperty>(XParameter("Param2", "Value2"))
        builder.build(
            from = VToDoUtil.build(prop),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            Properties.MIMETYPE to UnknownProperty.CONTENT_ITEM_TYPE,
            UNKNOWN_PROPERTY_DATA to "[\"X-Some-Property\",\"Some Value\",{\"Param1\":\"Value1\",\"Param2\":\"Value2\"}]"
        ), result.subValues.first().values)
        assertEquals(propertiesUri, result.subValues.first().uri)
    }

    @Test
    fun `Unknown property exceeding size limit is ignored`() {
        val result = Entity(ContentValues())
        val longValue = "x".repeat(UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE + 1)
        builder.build(
            from = VToDoUtil.build(XProperty("X-Huge-Property", longValue)),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `EXRULE is preserved as unknown property`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(ExRule<Temporal>(ParameterList(), "FREQ=WEEKLY")),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            Properties.MIMETYPE to UnknownProperty.CONTENT_ITEM_TYPE,
            UNKNOWN_PROPERTY_DATA to "[\"EXRULE\",\"FREQ=WEEKLY\"]"
        ), result.subValues.first().values)
    }

}
