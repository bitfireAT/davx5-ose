/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task

interface DmfsTaskPropertyHandler {

    /**
     * Takes specific data from a task property sub-row (taken from the content provider) and
     * maps it into the given [Task].
     *
     * Property sub-rows represent things like alarms, categories and relations.
     *
     * @param row   property sub-row from the content provider
     * @param to    destination object where the mapped data are stored
     */
    fun process(row: ContentValues, to: Task)

}
