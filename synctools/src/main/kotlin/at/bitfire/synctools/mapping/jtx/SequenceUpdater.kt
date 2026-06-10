/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx

import android.content.Entity
import at.techbee.jtx.JtxContract

class SequenceUpdater {

    /**
     * Increases the jtx object's SEQUENCE, if necessary. Usually called after a jtx object is
     * retrieved from the jtx content provider, but before it's mapped to an iCalendar.
     *
     * @param mainJtxObject  jtx object to be checked (**will be modified** when SEQUENCE needs to be increased)
     *
     * @return updated sequence (or *null* if sequence was not increased/modified)
     */
    fun increaseSequence(mainJtxObject: Entity): Int {
        val mainValues = mainJtxObject.entityValues
        val currentSeq = mainValues.getAsInteger(JtxContract.JtxICalObject.SEQUENCE)

        val newSeq = if (currentSeq == null) {
            // sequence has not been assigned yet (i.e. this jtx object was just locally created)
            0
        } else {
            // jtx object was modified, increase sequence
            currentSeq + 1
        }

        mainValues.put(JtxContract.JtxICalObject.SEQUENCE, newSeq)

        return newSeq
    }
}
