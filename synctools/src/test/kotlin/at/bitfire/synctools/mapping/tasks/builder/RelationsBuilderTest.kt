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
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.RelatedTo
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelationsBuilderTest {

    private val propertiesUri = Uri.parse("content://org.dmfs.tasks/properties")
    private val taskList = mockk<DmfsTaskList> {
        every { tasksPropertiesUri() } returns propertiesUri
    }
    private val builder = RelationsBuilder(taskList)

    @Test
    fun `old No relations - PARENT_ID reset to null`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.PARENT_ID))
        assertNull(result.entityValues.get(Tasks.PARENT_ID))
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `old RELATED-TO with PARENT type`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task().also {
                it.relatedTo += RelatedTo("parent-uid")
            },
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
            Relation.RELATED_UID to "parent-uid",
            Relation.RELATED_TYPE to Relation.RELTYPE_PARENT
        ), result.subValues.first().values)
        assertEquals(propertiesUri, result.subValues.first().uri)
    }

    @Test
    fun `old RELATED-TO with CHILD type`() {
        val result = Entity(ContentValues())
        val related = RelatedTo("child-uid").add<RelatedTo>(RelType.CHILD)
        builder.build(
            from = Task().also { it.relatedTo += related },
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
            Relation.RELATED_UID to "child-uid",
            Relation.RELATED_TYPE to Relation.RELTYPE_CHILD
        ), result.subValues.first().values)
    }

    @Test
    fun `old RELATED-TO with SIBLING type`() {
        val result = Entity(ContentValues())
        val related = RelatedTo("sibling-uid").add<RelatedTo>(RelType.SIBLING)
        builder.build(
            from = Task().also { it.relatedTo += related },
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
            Relation.RELATED_UID to "sibling-uid",
            Relation.RELATED_TYPE to Relation.RELTYPE_SIBLING
        ), result.subValues.first().values)
    }

    @Test
    fun `No relations - PARENT_ID reset to null`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Tasks.PARENT_ID))
        assertNull(result.entityValues.get(Tasks.PARENT_ID))
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `RELATED-TO with PARENT type`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VToDoUtil.build(RelatedTo("parent-uid")),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
            Relation.RELATED_UID to "parent-uid",
            Relation.RELATED_TYPE to Relation.RELTYPE_PARENT
        ), result.subValues.first().values)
        assertEquals(propertiesUri, result.subValues.first().uri)
    }

    @Test
    fun `RELATED-TO with CHILD type`() {
        val result = Entity(ContentValues())
        val related = RelatedTo("child-uid").add<RelatedTo>(RelType.CHILD)
        builder.build(
            from = VToDoUtil.build(related),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
            Relation.RELATED_UID to "child-uid",
            Relation.RELATED_TYPE to Relation.RELTYPE_CHILD
        ), result.subValues.first().values)
    }

    @Test
    fun `RELATED-TO with SIBLING type`() {
        val result = Entity(ContentValues())
        val related = RelatedTo("sibling-uid").add<RelatedTo>(RelType.SIBLING)
        builder.build(
            from = VToDoUtil.build(related),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(contentValuesOf(
            Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
            Relation.RELATED_UID to "sibling-uid",
            Relation.RELATED_TYPE to Relation.RELTYPE_SIBLING
        ), result.subValues.first().values)
    }

}
