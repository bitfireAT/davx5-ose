/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.DefaultTimezoneRule
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Created
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class CreatedBuilderTest {

    @get:Rule
    val tzRule = DefaultTimezoneRule("Europe/Berlin")

    private val builder = CreatedBuilder()

    @Test
    fun `No CREATED`() {
        val output = Entity(ContentValues())
        val task = VToDo()

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            contentValuesOf(JtxContract.JtxICalObject.CREATED to null),
            output.entityValues
        )
    }

    @Test
    fun `CREATED is UTC`() {
        val output = Entity(ContentValues())
        val instant = Instant.ofEpochMilli(1_000_000L)
        val task = VToDo().apply {
            this += Created(instant)
        }

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            contentValuesOf(JtxContract.JtxICalObject.CREATED to 1_000_000L),
            output.entityValues
        )
    }

    @Test
    fun `CREATED is zoned`() {
        val output = Entity(ContentValues())
        val instant = Instant.parse("2026-05-18T10:00:00Z")
        val task = VToDo().apply {
            this += Created(instant)
        }

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            contentValuesOf(JtxContract.JtxICalObject.CREATED to instant.toTimestamp()),
            output.entityValues
        )
    }

    @Test
    fun `CREATED string is interpreted as UTC`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Created("20240601T120000")
        }

        builder.build(from = task, main = task, to = output)

        val expectedTimestamp = Instant.parse("2024-06-01T12:00:00Z")
        assertContentValuesEqual(
            contentValuesOf(JtxContract.JtxICalObject.CREATED to expectedTimestamp.toTimestamp()),
            output.entityValues
        )
    }

    @Test
    fun `CREATED for VJournal`() {
        val output = Entity(ContentValues())
        val instant = Instant.parse("2026-06-01T08:00:00Z")
        val journal = VJournal(propertyListOf(Created(instant)))

        builder.build(from = journal, main = journal, to = output)

        assertContentValuesEqual(
            contentValuesOf(JtxContract.JtxICalObject.CREATED to instant.toTimestamp()),
            output.entityValues
        )
    }
}
