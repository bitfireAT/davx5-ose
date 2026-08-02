/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Uid
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class UidHandlerTest {

    private val handler = UidHandler()


    @Test
    fun `No UID`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.uid.getOrNull())
    }

    @Test
    fun `UID set`() {
        val input = Entity(contentValuesOf(Tasks._UID to "test-uid-123"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals("test-uid-123", task.uid.getOrNull()?.value)
    }

    @Test
    fun `exception should use UID from main jtx object`() {
        val from = Entity(ContentValues())
        val main = Entity(contentValuesOf(Tasks._UID to "test-uid-123"))
        val output = VToDo()

        handler.process(from = from, main = main, to = output)

        assertEquals(Uid("test-uid-123"), output.uid.getOrNull())
    }
}
