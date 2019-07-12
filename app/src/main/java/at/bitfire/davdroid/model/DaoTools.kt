package at.bitfire.davdroid.model

import at.bitfire.davdroid.log.Logger
import java.util.logging.Level

class DaoTools<T: IdEntity>(dao: SyncableDao<T>): SyncableDao<T> by dao {

    fun <K> syncAll(allOld: List<T>, allNew: Map<K,T>, selectKey: (T) -> K) {
        Logger.log.log(Level.FINE, "Syncing tables", arrayOf(allOld, allNew))
        val remainingNew = allNew.toMutableMap()
        allOld.forEach { old ->
            val key = selectKey(old)
            val matchingNew = remainingNew[key]
            if (matchingNew != null) {
                // keep this old item, but maybe update it
                matchingNew.id = old.id     // identity is proven by key
                if (matchingNew != old)
                    update(matchingNew)

                // remove from remainingNew
                remainingNew -= key
            } else {
                // this old item is not present anymore, delete it
                delete(old)
            }
        }
        insert(remainingNew.values.toList())
    }

}