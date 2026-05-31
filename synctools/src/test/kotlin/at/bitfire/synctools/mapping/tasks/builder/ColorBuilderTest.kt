/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.mapping.tasks.VToDoUtil.build
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Color
import org.dmfs.tasks.contract.TaskContract.Tasks
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
        val result = Entity(ContentValues())
        builder.build(
            from = VToDo(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.TASK_COLOR to null
        ), result.entityValues)
    }

    @Test
    fun `COLOR is set - css name`() {
        val result = Entity(ContentValues())
        builder.build(
            from = build(Color(null, Css3Color.nearestMatch(0xFF112233.toInt()).name)),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.TASK_COLOR to Css3Color.nearestMatch(0xFF112233.toInt()).argb
        ), result.entityValues)
    }

    @Test
    fun `COLOR is set - hex`() {
        val result = Entity(ContentValues())
        builder.build(
            from = build(Color(null, "#FF112233")),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.TASK_COLOR to 0xFF112233.toInt()
        ), result.entityValues)
    }

}
