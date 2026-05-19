/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.UNKNOWN_PROPERTY_DATA
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import org.dmfs.tasks.contract.TaskContract.Properties
import java.util.logging.Logger

class UnknownPropertiesBuilder(
    private val taskList: DmfsTaskList
) : DmfsTaskFieldBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: Task, to: Entity) {
        for (property in from.unknownProperties) {
            val value = property.value
            if (value == null) {
                logger.warning("Ignoring unknown property with null value")
                continue
            }
            if (value.length > UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE) {
                logger.warning("Ignoring unknown property with ${value.length} octets (too long)")
                continue
            }

            to.addSubValue(
                taskList.tasksPropertiesUri(),
                contentValuesOf(
                    Properties.MIMETYPE to UnknownProperty.CONTENT_ITEM_TYPE,
                    UNKNOWN_PROPERTY_DATA to UnknownProperty.toJsonString(property)
                )
            )
        }
    }

}
