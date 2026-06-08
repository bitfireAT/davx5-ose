/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.synctools.exception.InvalidLocalResourceException

interface DmfsTaskFieldHandler {

    /**
     * Takes specific data from a task row (taken from the content provider) and maps it into
     * the given [Task].
     *
     * @param from  task main row from the content provider
     * @param to    destination object where the mapped data are stored
     *
     * @throws InvalidLocalResourceException on missing or invalid required fields
     */
    fun process(from: ContentValues, to: Task)

}
