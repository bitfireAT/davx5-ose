/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import at.bitfire.davdroid.log.Logger
import java.util.logging.Level

class DaoTools<T: IdEntity>(dao: SyncableDao<T>): SyncableDao<T> by dao {

    /**
     * Synchronizes a list of "old" elements with a list of "new" elements so that the list
     * only contain equal elements.
     *
     * @param allOld      list of old elements
     * @param allNew      map of new elements (stored in key map)
     * @param selectKey   generates a unique key from the element (will be called on old elements)
     * @param prepareNew  prepares new elements (can be used to take over properties of old elements)
     */
    fun <K> syncAll(allOld: List<T>, allNew: Map<K,T>, selectKey: (T) -> K, prepareNew: (new: T, old: T) -> Unit = { _, _ -> }) {
        Logger.log.log(Level.FINE, "Syncing tables", arrayOf(allOld, allNew))
        val remainingNew = allNew.toMutableMap()
        allOld.forEach { old ->
            val key = selectKey(old)
            val matchingNew = remainingNew[key]
            if (matchingNew != null) {
                // keep this old item, but maybe update it
                matchingNew.id = old.id     // identity is proven by key
                prepareNew(matchingNew, old)

                if (matchingNew != old)
                    update(matchingNew)

                // remove from remainingNew
                remainingNew -= key
            } else {
                // this old item is not present anymore, delete it
                delete(old)
            }
        }

        val toInsert = remainingNew.values.toList()
        val insertIds = insert(toInsert)
        insertIds.withIndex().forEach { (idx, id) ->
            toInsert[idx].id = id
        }
    }

}