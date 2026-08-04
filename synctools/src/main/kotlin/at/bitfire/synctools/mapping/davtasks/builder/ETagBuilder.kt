/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.synctools.storage.davtasks.DavTasksContract.COLUMN_ETAG
import net.fortuna.ical4j.model.component.VToDo

class ETagBuilder(
    private val eTag: String?
) : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        to.entityValues.put(COLUMN_ETAG, eTag)
    }
}
