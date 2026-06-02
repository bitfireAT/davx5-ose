/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.RelatedTo
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class RelationsHandlerTest {

    private val handler = RelationsHandler()

    @Test
    fun `legacy No relation without UID`() {
        val task = Task()
        handler.process(contentValuesOf(
            Relation.RELATED_TYPE to Relation.RELTYPE_PARENT
        ), task)
        assertTrue(task.relatedTo.isEmpty())
    }

    @Test
    fun `legacy Parent relation`() {
        val task = Task()
        handler.process(contentValuesOf(
            Relation.RELATED_UID to "parent-uid",
            Relation.RELATED_TYPE to Relation.RELTYPE_PARENT
        ), task)

        assertEquals(1, task.relatedTo.size)
        val relatedTo = task.relatedTo.first()
        assertEquals("parent-uid", relatedTo.value)
        assertEquals(RelType.PARENT, relatedTo.getParameter<RelType>(Parameter.RELTYPE)?.getOrNull())
    }

    @Test
    fun `legacy Child relation`() {
        val task = Task()
        handler.process(contentValuesOf(
            Relation.RELATED_UID to "child-uid",
            Relation.RELATED_TYPE to Relation.RELTYPE_CHILD
        ), task)

        assertEquals(1, task.relatedTo.size)
        val relatedTo = task.relatedTo.first()
        assertEquals("child-uid", relatedTo.value)
        assertEquals(RelType.CHILD, relatedTo.getParameter<RelType>(Parameter.RELTYPE)?.getOrNull())
    }

    @Test
    fun `legacy Sibling relation`() {
        val task = Task()
        handler.process(contentValuesOf(
            Relation.RELATED_UID to "sibling-uid",
            Relation.RELATED_TYPE to Relation.RELTYPE_SIBLING
        ), task)

        assertEquals(1, task.relatedTo.size)
        val relatedTo = task.relatedTo.first()
        assertEquals("sibling-uid", relatedTo.value)
        assertEquals(RelType.SIBLING, relatedTo.getParameter<RelType>(Parameter.RELTYPE)?.getOrNull())
    }

    @Test
    fun `legacy Default to parent when type not specified`() {
        val task = Task()
        handler.process(contentValuesOf(
            Relation.RELATED_UID to "default-uid"
        ), task)

        assertEquals(1, task.relatedTo.size)
        val relatedTo = task.relatedTo.first()
        assertEquals("default-uid", relatedTo.value)
        assertEquals(RelType.PARENT, relatedTo.getParameter<RelType>(Parameter.RELTYPE)?.getOrNull())
    }

    @Test
    fun `legacy Multiple relations accumulate`() {
        val task = Task()
        handler.process(contentValuesOf(
            Relation.RELATED_UID to "parent-uid",
            Relation.RELATED_TYPE to Relation.RELTYPE_PARENT
        ), task)
        handler.process(contentValuesOf(
            Relation.RELATED_UID to "child-uid",
            Relation.RELATED_TYPE to Relation.RELTYPE_CHILD
        ), task)

        assertEquals(2, task.relatedTo.size)
    }

    @Test
    fun `No relation without UID`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
                Relation.RELATED_TYPE to Relation.RELTYPE_PARENT
            ))
        }
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertTrue(task.getProperties<RelatedTo>(Priority.RELATED_TO).isEmpty())
    }

    @Test
    fun `Parent relation`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
                Relation.RELATED_UID to "parent-uid",
                Relation.RELATED_TYPE to Relation.RELTYPE_PARENT
            ))
        }
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        val relatedToList = task.getProperties<RelatedTo>(Property.RELATED_TO)
        assertEquals(1, relatedToList.size)
        assertEquals(RelatedTo(ParameterList(listOf(RelType.PARENT)), "parent-uid"), relatedToList[0])
    }

    @Test
    fun `Child relation`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
                Relation.RELATED_UID to "child-uid",
                Relation.RELATED_TYPE to Relation.RELTYPE_CHILD
            ))
        }
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        val relatedToList = task.getProperties<RelatedTo>(Property.RELATED_TO)
        assertEquals(1, relatedToList.size)
        assertEquals(RelatedTo(ParameterList(listOf(RelType.CHILD)), "child-uid"), relatedToList[0])
    }

    @Test
    fun `Sibling relation`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
                Relation.RELATED_UID to "sibling-uid",
                Relation.RELATED_TYPE to Relation.RELTYPE_SIBLING
            ))
        }
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        val relatedToList = task.getProperties<RelatedTo>(Property.RELATED_TO)
        assertEquals(1, relatedToList.size)
        assertEquals(RelatedTo(ParameterList(listOf(RelType.SIBLING)), "sibling-uid"), relatedToList[0])
    }

    @Test
    fun `Default to parent when type not specified`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
                Relation.RELATED_UID to "default-uid"
            ))
        }
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        val relatedToList = task.getProperties<RelatedTo>(Property.RELATED_TO)
        assertEquals(1, relatedToList.size)
        assertEquals(RelatedTo(ParameterList(listOf(RelType.PARENT)), "default-uid"), relatedToList[0])
    }

    @Test
    fun `Multiple relations accumulate`() {
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
                Relation.RELATED_UID to "parent-uid",
                Relation.RELATED_TYPE to Relation.RELTYPE_PARENT
            ))
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
                Relation.RELATED_UID to "child-uid",
                Relation.RELATED_TYPE to Relation.RELTYPE_CHILD
            ))
        }
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        val relatedToList = task.getProperties<RelatedTo>(Property.RELATED_TO).sortedBy { it.value }
        assertEquals(2, relatedToList.size)
        assertEquals(RelatedTo(ParameterList(listOf(RelType.CHILD)), "child-uid"), relatedToList[0])
        assertEquals(RelatedTo(ParameterList(listOf(RelType.PARENT)), "parent-uid"), relatedToList[1])
    }
}
