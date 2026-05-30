/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Property.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommentsHandlerTest {

    private val handler = CommentsHandler()

    @Test
    fun `No comment`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.comment)
    }

    @Test
    fun `Comment set`() {
        val task = Task()
        handler.process(contentValuesOf(Comment.COMMENT to "Task comment"), task)
        assertEquals("Task comment", task.comment)
    }

    @Test
    fun `Comment overwritten by subsequent call`() {
        val task = Task()
        handler.process(contentValuesOf(Comment.COMMENT to "First comment"), task)
        handler.process(contentValuesOf(Comment.COMMENT to "Second comment"), task)

        assertEquals("Second comment", task.comment)
    }

    @Test
    fun `Null comment is skipped`() {
        val task = Task()
        handler.process(ContentValues().apply {
            putNull(Comment.COMMENT)
        }, task)
        assertNull(task.comment)
    }

}
