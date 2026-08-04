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
import net.fortuna.ical4j.model.property.Categories

/** RFC 5545 §3.8.1.2 CATEGORIES — one row per category value. */
class CategoriesBuilder(
    private val taskList: DavTaskList
) : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        for (categoriesProp in from.getProperties<Categories>(Property.CATEGORIES)) {
            for (category in categoriesProp.categories.texts) {
                to.addSubValue(
                    taskList.tasksPropertiesUri(asSyncAdapter = false),
                    contentValuesOf(
                        TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_CATEGORY,
                        TaskProperties.DATA1 to category
                    )
                )
            }
        }
    }
}
