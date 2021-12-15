/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import java.util.*

object ConcurrentUtils {

    private val running = Collections.synchronizedSet(HashSet<Any>())


    /**
     * Guards a code block by a key – the block will only run when there is currently no
     * other running code block with the same key (compared by [Object.equals]).
     *
     * @param key       guarding key to determine whether the code block will be run
     * @param block     this code block will be run, but not more than one time at once per key
     *
     * @return  *true* if the code block was executed (i.e. there was no running code block with this key);
     *          *false* if there was already another running block with that key, so that the code block wasn't executed
     */
    fun runSingle(key: Any, block: () -> Unit): Boolean {
        if (!running.add(key))      // already running?
            return false            // this key is already in use, refuse execution
        // key is now in running

        try {
            block()
            return true

        } finally {
            running.remove(key)
            // key is now not in running anymore; further calls will succeed
        }
    }

}