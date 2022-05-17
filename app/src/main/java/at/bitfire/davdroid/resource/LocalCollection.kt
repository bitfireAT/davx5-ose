/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.provider.CalendarContract.Events
import at.bitfire.davdroid.db.SyncState

interface LocalCollection<out T: LocalResource<*>> {

    /** a tag that uniquely identifies the collection (DAVx5-wide) */
    val tag: String

    /** collection title (used for user notifications etc.) **/
    val title: String

    var lastSyncState: SyncState?

    /**
     * Finds local resources of this collection which have been marked as *deleted* by the user
     * or an app acting on their behalf.
     *
     * @return list of resources marked as *deleted*
     */
    fun findDeleted(): List<T>

    /**
     * Finds local resources of this collection which have been marked as *dirty*, i.e. resources
     * which have been modified by the user or an app acting on their behalf.
     *
     * @return list of resources marked as *dirty*
     */
    fun findDirty(): List<T>

    /**
     * Finds a local resource of this collection with a given file name. (File names are assigned
     * by the sync adapter.)
     *
     * @param name file name to look for
     * @return resource with the given name, or null if none
     */
    fun findByName(name: String): T?


    /**
     * Sets the [LocalEvent.COLUMN_FLAGS] value for entries which are not dirty ([Events.DIRTY] is 0)
     * and have an [Events.ORIGINAL_ID] of null.
     *
     * @param flags    value of flags to set (for instance, [LocalResource.FLAG_REMOTELY_PRESENT]])
     *
     * @return         number of marked entries
     */
    fun markNotDirty(flags: Int): Int

    /**
     * Removes entries which are not dirty ([Events.DIRTY] is 0 and an [Events.ORIGINAL_ID] is null) with
     * a given flag combination.
     *
     * @param flags    exact flags value to remove entries with (for instance, if this is [LocalResource.FLAG_REMOTELY_PRESENT]],
     *                 all entries with exactly this flag will be removed)
     *
     * @return         number of removed entries
     */
    fun removeNotDirtyMarked(flags: Int): Int


    /**
     * Forgets the ETags of all members so that they will be reloaded from the server during sync.
     */
    fun forgetETags()

}
