/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Property.Category

class CategoriesHandler : DmfsTaskPropertyHandler {
    override fun process(row: ContentValues, to: Task) {
        row.getAsString(Category.CATEGORY_NAME)?.let { to.categories += it }
    }
}
