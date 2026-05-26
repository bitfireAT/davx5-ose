/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import org.dmfs.tasks.contract.TaskContract.Property.Category

class CategoriesBuilder(
    private val taskList: DmfsTaskList
) : DmfsTaskFieldBuilder {

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

}
