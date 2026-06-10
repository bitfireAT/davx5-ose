/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceUpdaterTest {

    private val updater = SequenceUpdater()

    @Test
    fun `current SEQUENCE is null`() {
        val jtxObject = Entity(ContentValues())

        val newSeq = updater.increaseSequence(jtxObject)

        assertEquals(0, newSeq)
        assertEquals(0, jtxObject.entityValues.getAsInteger(JtxContract.JtxICalObject.SEQUENCE))
    }

    @Test
    fun `current SEQUENCE is 0`() {
        val jtxObject = Entity(
            contentValuesOf(JtxContract.JtxICalObject.SEQUENCE to 0)
        )

        val newSeq = updater.increaseSequence(jtxObject)

        assertEquals(1, newSeq)
        assertEquals(1, jtxObject.entityValues.getAsInteger(JtxContract.JtxICalObject.SEQUENCE))
    }

    @Test
    fun `current SEQUENCE is 1`() {
        val jtxObject = Entity(
            contentValuesOf(JtxContract.JtxICalObject.SEQUENCE to 1)
        )

        val newSeq = updater.increaseSequence(jtxObject)

        assertEquals(2, newSeq)
        assertEquals(2, jtxObject.entityValues.getAsInteger(JtxContract.JtxICalObject.SEQUENCE))
    }
}
