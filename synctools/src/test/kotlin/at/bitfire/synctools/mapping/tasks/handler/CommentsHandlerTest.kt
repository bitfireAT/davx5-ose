/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
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
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                DmfsComment.MIMETYPE to DmfsComment.CONTENT_ITEM_TYPE,
                DmfsComment.COMMENT to "Task comment"
            ))
        }
        handler.process(from = input, main = input, to = vToDo)

        val comment = vToDo.getProperty<Comment>(Property.COMMENT).get()
        assertEquals("Task comment", comment.value)
    }

    @Test
    fun `Comment overwritten by subsequent call`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                DmfsComment.MIMETYPE to DmfsComment.CONTENT_ITEM_TYPE,
                DmfsComment.COMMENT to "First comment"
            ))
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                DmfsComment.MIMETYPE to DmfsComment.CONTENT_ITEM_TYPE,
                DmfsComment.COMMENT to "Second comment"
            ))
        }
        handler.process(from = input, main = input, to = vToDo)

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
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                DmfsComment.MIMETYPE to DmfsComment.CONTENT_ITEM_TYPE
            ).apply {
                putNull(DmfsComment.COMMENT)
            })
        }
        handler.process(from = input, main = input, to = vToDo)

        val comment = vToDo.getProperty<Comment>(Property.COMMENT).getOrNull()
        assertNull(comment)
    }

}
