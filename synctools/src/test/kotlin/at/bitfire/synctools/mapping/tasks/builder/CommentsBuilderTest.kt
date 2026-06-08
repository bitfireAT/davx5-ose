/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.test.assertContentValuesEqual
import io.mockk.every
import io.mockk.mockk
import net.fortuna.ical4j.model.property.Comment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.dmfs.tasks.contract.TaskContract.Property.Comment as DmfsComment

@RunWith(RobolectricTestRunner::class)
class CommentsBuilderTest {

    private val propertiesUri = Uri.parse("content://org.dmfs.tasks/properties")
    private val taskList = mockk<DmfsTaskList> {
        every { tasksPropertiesUri() } returns propertiesUri
    }
    private val builder = CommentsBuilder(taskList)

    @Test
    fun `old No comment`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `old Comment is set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(comment = "This is a comment"),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            DmfsComment.MIMETYPE to DmfsComment.CONTENT_ITEM_TYPE,
            DmfsComment.COMMENT to "This is a comment"
        ), result.subValues.first().values)
        assertEquals(propertiesUri, result.subValues.first().uri)
    }

    @Test
    fun `No comment`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `Comment is set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Comment("This is a comment")),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            DmfsComment.MIMETYPE to DmfsComment.CONTENT_ITEM_TYPE,
            DmfsComment.COMMENT to "This is a comment"
        ), result.subValues.first().values)
        assertEquals(propertiesUri, result.subValues.first().uri)
    }

    @Test
    fun `Multiple comments`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(Comment("First comment"), Comment("Second comment")),
            to = result
        )
        assertEquals(2, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            DmfsComment.MIMETYPE to DmfsComment.CONTENT_ITEM_TYPE,
            DmfsComment.COMMENT to "First comment"
        ), result.subValues[0].values)
        assertContentValuesEqual(contentValuesOf(
            DmfsComment.MIMETYPE to DmfsComment.CONTENT_ITEM_TYPE,
            DmfsComment.COMMENT to "Second comment"
        ), result.subValues[1].values)
    }

}
