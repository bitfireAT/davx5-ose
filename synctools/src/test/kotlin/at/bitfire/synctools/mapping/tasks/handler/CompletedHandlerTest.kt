/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.Completed
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class CompletedHandlerTest {

    private val handler = CompletedHandler()

    @Test
    fun `No COMPLETED leaves completedAt null`() {
        val task = Task()
        handler.process(ContentValues(), task)
        assertNull(task.completedAt)
    }

    @Test
    fun `COMPLETED epoch millis is mapped correctly`() {
        val task = Task()
        val epochMillis = 1_700_000_000_000L
        handler.process(contentValuesOf(Tasks.COMPLETED to epochMillis), task)
        assertEquals(Completed(Instant.ofEpochMilli(epochMillis)), task.completedAt)
    }

}
