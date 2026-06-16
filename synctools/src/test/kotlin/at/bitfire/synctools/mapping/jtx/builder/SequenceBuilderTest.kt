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
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Sequence
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceBuilderTest {

    private val builder = SequenceBuilder()

    @Test
    fun `No SEQUENCE defaults to 0`() {
        val output = Entity(ContentValues())
        val task = VToDo()

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            contentValuesOf(JtxContract.JtxICalObject.SEQUENCE to 0),
            output.entityValues
        )
    }

    @Test
    fun `SEQUENCE is 0`() {
        val output = Entity(ContentValues())
        val task = VToDo(propertyListOf(Sequence(0)))

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            contentValuesOf(JtxContract.JtxICalObject.SEQUENCE to 0),
            output.entityValues
        )
    }

    @Test
    fun `SEQUENCE is 1`() {
        val output = Entity(ContentValues())
        val task = VToDo(propertyListOf(Sequence(1)))

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            contentValuesOf(JtxContract.JtxICalObject.SEQUENCE to 1),
            output.entityValues
        )
    }

    @Test
    fun `SEQUENCE is 5`() {
        val output = Entity(ContentValues())
        val journal = VJournal(propertyListOf(Sequence(5)))

        builder.build(from = journal, main = journal, to = output)

        assertContentValuesEqual(
            contentValuesOf(JtxContract.JtxICalObject.SEQUENCE to 5),
            output.entityValues
        )
    }
}
