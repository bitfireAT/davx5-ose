/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Resources

/** RFC 5545 §3.8.1.10 RESOURCES — one row per resource value. */
class ResourcesBuilder(
    private val taskList: DavTaskList
) : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        for (resources in from.getProperties<Resources>(Property.RESOURCES)) {
            for (resource in resources.resources.texts) {
                to.addSubValue(
                    taskList.tasksPropertiesUri(asSyncAdapter = false),
                    contentValuesOf(
                        TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_RESOURCE,
                        TaskProperties.DATA1 to resource
                    )
                )
            }
        }
    }
}
