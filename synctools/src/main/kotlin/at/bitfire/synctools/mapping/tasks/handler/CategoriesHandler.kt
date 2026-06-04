/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Categories
import org.dmfs.tasks.contract.TaskContract.Property.Category

class CategoriesHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {
    override fun process(from: ContentValues, to: Task) {
        from.getAsString(Category.CATEGORY_NAME)?.let { to.categories += it }
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        from.entityValues.getAsString(Category.CATEGORY_NAME)?.let { categoryName ->
            to += Categories(TextList(categoryName))
        }
    }
}
