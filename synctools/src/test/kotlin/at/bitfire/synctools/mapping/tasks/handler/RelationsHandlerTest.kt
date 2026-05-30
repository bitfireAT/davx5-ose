/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.RelType
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
    fun `No relation without UID`() {
        val task = Task()
        handler.process(contentValuesOf(
            Relation.RELATED_TYPE to Relation.RELTYPE_PARENT
        ), task)
        assertTrue(task.relatedTo.isEmpty())
    }

    @Test
    fun `Parent relation`() {
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
    fun `Child relation`() {
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
    fun `Sibling relation`() {
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
    fun `Default to parent when type not specified`() {
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
    fun `Multiple relations accumulate`() {
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

}
