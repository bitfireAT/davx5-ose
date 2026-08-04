/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.RelatedTo
import kotlin.jvm.optionals.getOrNull

/**
 * RFC 5545 §3.8.4.5 RELATED-TO, extended by RFC 9253 task-dependency RELTYPE values — unlike the
 * DMFS backend, the raw RELTYPE parameter string is passed through as-is (not restricted to
 * PARENT/CHILD/SIBLING), so DEPENDS-ON/FINISHTOSTART/etc. round-trip losslessly.
 */
class RelationsBuilder(
    private val taskList: DavTaskList
) : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        for (relatedTo in from.getProperties<RelatedTo>(Property.RELATED_TO)) {
            val relType = relatedTo.getParameter<RelType>(Parameter.RELTYPE).getOrNull()?.value
                ?: TaskProperties.RelType.PARENT
            to.addSubValue(
                taskList.tasksPropertiesUri(asSyncAdapter = false),
                contentValuesOf(
                    TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_RELATION,
                    TaskProperties.DATA1 to relatedTo.value,
                    TaskProperties.DATA2 to relType
                )
            )
        }
    }
}
