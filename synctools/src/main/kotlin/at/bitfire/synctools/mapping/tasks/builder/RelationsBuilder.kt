/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.RelatedTo
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

/**
 * Note: This Sub-row builder alters the main-row [Tasks.PARENT_ID]
 */
class RelationsBuilder(
    private val taskList: DmfsTaskList
) : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        // parent_id will be re-calculated when the relation row is inserted (if there is any)
        to.entityValues.put(Tasks.PARENT_ID, null as Long?)

        for (relatedTo in from.relatedTo) {
            val relType = when (relatedTo.getParameter<RelType>(Parameter.RELTYPE).getOrNull()) {
                RelType.CHILD   -> Relation.RELTYPE_CHILD
                RelType.SIBLING -> Relation.RELTYPE_SIBLING
                else /* RelType.PARENT, default value */ -> Relation.RELTYPE_PARENT
            }
            to.addSubValue(
                taskList.tasksPropertiesUri(),
                contentValuesOf(
                    Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
                    Relation.RELATED_UID to relatedTo.value,
                    Relation.RELATED_TYPE to relType
                )
            )
        }
    }

    override fun build(from: VToDo, to: Entity) {
        // parent_id will be re-calculated when the relation row is inserted (if there is any)
        to.entityValues.put(Tasks.PARENT_ID, null as Long?)

        for (relatedTo in from.getProperties<RelatedTo>(Property.RELATED_TO)) {
            val relType = when (relatedTo.getParameter<RelType>(Parameter.RELTYPE).getOrNull()) {
                RelType.CHILD   -> Relation.RELTYPE_CHILD
                RelType.SIBLING -> Relation.RELTYPE_SIBLING
                else /* RelType.PARENT, default value */ -> Relation.RELTYPE_PARENT
            }
            to.addSubValue(
                taskList.tasksPropertiesUri(),
                contentValuesOf(
                    Relation.MIMETYPE to Relation.CONTENT_ITEM_TYPE,
                    Relation.RELATED_UID to relatedTo.value,
                    Relation.RELATED_TYPE to relType
                )
            )
        }
    }

}
