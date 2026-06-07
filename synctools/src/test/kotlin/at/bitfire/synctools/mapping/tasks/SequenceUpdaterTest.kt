/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNull
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceUpdaterTest {

    private val updater = SequenceUpdater()

    @Test
    fun `Current SEQUENCE is null`() {
        val task = Entity(ContentValues())
        val newSeq = updater.increaseSequence(task)
        assertEquals(0, newSeq)
        assertNull(task.entityValues.getAsInteger(Tasks.SYNC_VERSION))
    }

    @Test
    fun `Current SEQUENCE is 0`() {
        val task = Entity(contentValuesOf(Tasks.SYNC_VERSION to 0))
        val newSeq = updater.increaseSequence(task)
        assertEquals(1, newSeq)
        assertEquals(0, task.entityValues.getAsInteger(Tasks.SYNC_VERSION))
    }

    @Test
    fun `Current SEQUENCE is 1`() {
        val task = Entity(contentValuesOf(Tasks.SYNC_VERSION to 1))
        val newSeq = updater.increaseSequence(task)
        assertEquals(2, newSeq)
        assertEquals(1, task.entityValues.getAsInteger(Tasks.SYNC_VERSION))
    }

}
