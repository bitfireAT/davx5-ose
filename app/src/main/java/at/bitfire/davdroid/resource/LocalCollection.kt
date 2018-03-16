/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import at.bitfire.davdroid.model.SyncState

interface LocalCollection<out T: LocalResource<*>> {

    /** collection title (used for user notifications etc.) **/
    val title: String

    var lastSyncState: SyncState?

    fun findDeleted(): List<T>
    fun findDirty(): List<T>

    fun findByName(name: String): T?


    /**
     * Marks all entries which are not dirty with the given flags only.
     * @return number of marked entries
     **/
    fun markNotDirty(flags: Int): Int

    /**
     * Removes all entries with are not dirty and are marked with exactly the given flags.
     * @return number of removed entries
     */
    fun removeNotDirtyMarked(flags: Int): Int

}
