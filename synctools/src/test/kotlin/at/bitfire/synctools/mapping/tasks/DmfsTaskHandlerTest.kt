/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.tasks.TaskAndExceptions
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Uid
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DmfsTaskHandlerTest {

    private val handler = DmfsTaskHandler(prodId = ProdId(javaClass.simpleName))

    @Test
    fun `mapToVToDos adds UID if necessary`() {
        val taskAndExceptions = TaskAndExceptions(
            main = Entity(
                contentValuesOf(
                    Tasks._UID to null
                )
            ),
            exceptions = emptyList()
        )

        val result = handler.mapToVToDos(taskAndExceptions)

        assertTrue(result.generatedUid)
    }

    @Test
    fun `mapToVToDos uses UID if present`() {
        val taskAndExceptions = TaskAndExceptions(
            main = Entity(
                contentValuesOf(
                    Tasks._UID to "uid"
                )
            ),
            exceptions = emptyList()
        )

        val result = handler.mapToVToDos(taskAndExceptions)

        assertEquals("uid", result.associatedTasks.main!!.getRequiredProperty<Uid>(Property.UID).value)
    }

    @Test
    fun `mapToVToDos adds DTSTAMP property`() {
        val taskAndExceptions = TaskAndExceptions(
            main = Entity(ContentValues()),
            exceptions = emptyList()
        )

        val result = handler.mapToVToDos(taskAndExceptions)

        assertTrue(result.associatedTasks.main!!.getProperty<DtStamp>(Property.DTSTAMP).isPresent)
    }

    @Test
    fun `mapToVToDos with multiple RELATED-TO properties`() {
        val taskAndExceptions = TaskAndExceptions(
            main = Entity(ContentValues()).apply {
                addSubValue(
                    Properties.getContentUri("tasks"),
                    contentValuesOf(
                        Properties.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
                        Relation.RELATED_UID to "parent",
                        Relation.RELATED_TYPE to Relation.RELTYPE_PARENT
                    )
                )
                addSubValue(
                    Properties.getContentUri("tasks"),
                    contentValuesOf(
                        Properties.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
                        Relation.RELATED_UID to "child",
                        Relation.RELATED_TYPE to Relation.RELTYPE_CHILD
                    )
                )
            },
            exceptions = emptyList()
        )

        val result = handler.mapToVToDos(taskAndExceptions)

        val relatedToList = result.associatedTasks.main!!.getProperties<RelatedTo>(Property.RELATED_TO)
            .sortedBy { it.getParameter<RelType>(Parameter.RELTYPE).get().value }
        assertEquals(RelType.CHILD, relatedToList[0].getRequiredParameter<RelType>(Parameter.RELTYPE))
        assertEquals("child", relatedToList[0].value)
        assertEquals(RelType.PARENT, relatedToList[1].getRequiredParameter<RelType>(Parameter.RELTYPE))
        assertEquals("parent", relatedToList[1].value)
    }
}
