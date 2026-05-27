/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.UNKNOWN_PROPERTY_DATA

class UnknownPropertiesHandler : DmfsTaskPropertyHandler {
    override fun process(row: ContentValues, to: Task) {
        row.getAsString(UNKNOWN_PROPERTY_DATA)?.let { properties ->
            to.unknownProperties += UnknownProperty.fromJsonString(properties)
        }
    }
}
