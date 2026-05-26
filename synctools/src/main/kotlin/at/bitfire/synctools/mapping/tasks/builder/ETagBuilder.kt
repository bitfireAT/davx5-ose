/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.COLUMN_ETAG

class ETagBuilder(
    private val eTag: String?
) : DmfsTaskFieldBuilder {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(COLUMN_ETAG, eTag)
    }

}
