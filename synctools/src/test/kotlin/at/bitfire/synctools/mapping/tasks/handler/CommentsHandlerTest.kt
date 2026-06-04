/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull
import org.dmfs.tasks.contract.TaskContract.Property.Comment as DmfsComment

@RunWith(RobolectricTestRunner::class)
class CommentsHandlerTest {

    private val handler = CommentsHandler()

    @Test
    fun `legacy No comment`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.comment)
    }

    @Test
    fun `legacy Comment set`() {
        val task = Task()
        handler.process(contentValuesOf(DmfsComment.COMMENT to "Task comment"), task)
        assertEquals("Task comment", task.comment)
    }

    @Test
    fun `legacy Comment overwritten by subsequent call`() {
        val task = Task()
        handler.process(contentValuesOf(DmfsComment.COMMENT to "First comment"), task)
        handler.process(contentValuesOf(DmfsComment.COMMENT to "Second comment"), task)

        assertEquals("Second comment", task.comment)
    }

    @Test
    fun `legacy Null comment is skipped`() {
        val task = Task()
        handler.process(ContentValues().apply {
            putNull(DmfsComment.COMMENT)
        }, task)
        assertNull(task.comment)
    }

    @Test
    fun `No comment`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues())
        handler.process(from = input, main = input, to = vToDo)

        val comment = vToDo.getProperty<Comment>(Property.COMMENT).getOrNull()
        assertNull(comment)
    }

    @Test
    fun `Comment set`() {
        val vToDo = VToDo()
        val input = Entity(contentValuesOf(DmfsComment.COMMENT to "Task comment"))
        handler.process(from = input, main = input, to = vToDo)

        val comment = vToDo.getProperty<Comment>(Property.COMMENT).get()
        assertEquals("Task comment", comment.value)
    }

    @Test
    fun `Comment overwritten by subsequent call`() {
        val vToDo = VToDo()
        val input1 = Entity(contentValuesOf(DmfsComment.COMMENT to "First comment"))
        val input2 = Entity(contentValuesOf(DmfsComment.COMMENT to "Second comment"))
        handler.process(from = input1, main = input1, to = vToDo)
        handler.process(from = input2, main = input2, to = vToDo)

        // With current implementation, each comment is added as a separate Comment property
        // So we should have 2 properties.
        val comments = vToDo.getProperties<Comment>(Property.COMMENT)
        assertEquals(2, comments.size)
        assertEquals("First comment", comments.first().value)
        assertEquals("Second comment", comments.last().value)
    }

    @Test
    fun `Null comment is skipped`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues().apply {
            putNull(DmfsComment.COMMENT)
        })
        handler.process(from = input, main = input, to = vToDo)

        val comment = vToDo.getProperty<Comment>(Property.COMMENT).getOrNull()
        assertNull(comment)
    }

}
