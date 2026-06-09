/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Completed
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.Optional

@RunWith(RobolectricTestRunner::class)
class CompletedHandlerTest {

    private val handler = CompletedHandler()


    @Test
    fun `No COMPLETED leaves completedAt null`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Optional.empty<Completed>(), task.getProperty<Completed>(Property.COMPLETED))
    }

    @Test
    fun `COMPLETED epoch millis is mapped correctly`() {
        val epochMillis = 1_700_000_000_000L
        val input = Entity(contentValuesOf(Tasks.COMPLETED to epochMillis))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(Instant.ofEpochMilli(epochMillis), task.getRequiredProperty<Completed>(Property.COMPLETED).date)
    }
}
