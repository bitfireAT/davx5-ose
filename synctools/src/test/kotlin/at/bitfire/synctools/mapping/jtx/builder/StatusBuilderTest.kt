/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject.StatusJournal
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusBuilderTest {

    private val builder = StatusBuilder()

    @Test
    fun `No STATUS`() {
        val task = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.containsKey(JtxContract.JtxICalObject.STATUS))
        assertNull(output.entityValues.get(JtxContract.JtxICalObject.STATUS))
    }

    @Test
    fun `STATUS is DRAFT`() {
        val journal = VJournal(propertyListOf(ImmutableStatus.VJOURNAL_DRAFT))
        val main = VJournal()
        val output = Entity(ContentValues())

        builder.build(from = journal, main = main, to = output)

        assertContentValuesEqual(
            contentValuesOf(JtxContract.JtxICalObject.STATUS to StatusJournal.DRAFT.name),
            output.entityValues
        )
    }

    @Test
    fun `Unknown STATUS is kept`() {
        val journal = VJournal(propertyListOf(Status("unknown")))
        val output = Entity(ContentValues())

        builder.build(from = journal, main = journal, to = output)

        assertContentValuesEqual(
            contentValuesOf(JtxContract.JtxICalObject.STATUS to "unknown"),
            output.entityValues
        )
    }
}
