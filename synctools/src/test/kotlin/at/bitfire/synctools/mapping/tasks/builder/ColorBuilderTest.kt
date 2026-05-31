/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Color
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ColorBuilderTest {

    private val builder = ColorBuilder()

    @Test
    fun `old No COLOR`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.TASK_COLOR to null
        ), result.entityValues)
    }

    @Test
    fun `old COLOR is set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(color = 0xFF112233.toInt()),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.TASK_COLOR to 0xFF112233.toInt()
        ), result.entityValues)
    }

    @Test
    fun `No COLOR`() {
        val result = VToDo()
        builder.build(
            from = Task(),
            to = result
        )
        val color = result.getProperty<Color>(Color.PROPERTY_NAME)
        assertTrue(color.isPresent)
        assertNull(color.get().value)
    }

    @Test
    fun `COLOR is set`() {
        val result = VToDo()
        builder.build(
            from = Task(color = 0xFF112233.toInt()),
            to = result
        )
        assertEquals(
            Css3Color.nearestMatch(0xFF112233.toInt()).name,
            result.getRequiredProperty<Color>(Color.PROPERTY_NAME).value
        )
    }

}
