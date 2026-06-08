/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Categories
import org.dmfs.tasks.contract.TaskContract.Property.Category

class CategoriesBuilder(
    private val taskList: DmfsTaskList
) : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        for (category in from.categories) {
            to.addSubValue(
                taskList.tasksPropertiesUri(),
                contentValuesOf(
                    Category.MIMETYPE to Category.CONTENT_ITEM_TYPE,
                    Category.CATEGORY_NAME to category
                )
            )
        }
    }

    override fun build(from: VToDo, to: Entity) {
        for (categoriesProp in from.getProperties<Categories>(Property.CATEGORIES)) {
            for (category in categoriesProp.categories.texts) {
                to.addSubValue(
                    taskList.tasksPropertiesUri(),
                    contentValuesOf(
                        Category.MIMETYPE to Category.CONTENT_ITEM_TYPE,
                        Category.CATEGORY_NAME to category
                    )
                )
            }
        }
    }

}
