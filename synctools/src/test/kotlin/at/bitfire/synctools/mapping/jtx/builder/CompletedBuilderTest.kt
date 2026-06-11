/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.DefaultTimezoneRule
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.test.assertContentValuesEqual
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class CompletedBuilderTest {

    @get:Rule
    val tzRule = DefaultTimezoneRule("Europe/Berlin")

    private val builder = CompletedBuilder()

    @Test
    fun `No COMPLETED`() {
        val output = Entity(ContentValues())
        val task = VToDo()

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.COMPLETED to null,
                JtxContract.JtxICalObject.COMPLETED_TIMEZONE to null
            ),
            output.entityValues
        )
    }

    @Test
    fun `COMPLETED is UTC`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Completed(Instant.ofEpochMilli(1_000_000L))
            this += XProperty(JtxICalObject.X_PROP_COMPLETEDTIMEZONE, "Z")
        }

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.COMPLETED to 1_000_000L,
                JtxContract.JtxICalObject.COMPLETED_TIMEZONE to "Z"
            ),
            output.entityValues
        )
    }

    @Test
    fun `COMPLETED is zoned`() {
        val completed = Instant.parse("2026-05-18T10:00:00Z")
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Completed(completed) // Always in UTC
            this += XProperty(JtxICalObject.X_PROP_COMPLETEDTIMEZONE, "Europe/Vienna")
        }

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.COMPLETED to completed.toTimestamp(),
                JtxContract.JtxICalObject.COMPLETED_TIMEZONE to "Europe/Vienna"
            ),
            output.entityValues
        )
    }

    @Test
    fun `COMPLETED string is interpreted as UTC`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Completed("20240601T120000") // Always in UTC
        }

        builder.build(from = task, main = task, to = output)

        val expectedTimestamp = Instant.parse("2024-06-01T12:00:00Z")
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.COMPLETED to expectedTimestamp.toTimestamp(),
                JtxContract.JtxICalObject.COMPLETED_TIMEZONE to null
            ),
            output.entityValues
        )
    }

    @Test
    fun `COMPLETED is DATE`() {
        val output = Entity(ContentValues())
        val task = VToDo().apply {
            this += Completed(ParameterList(listOf(Value.DATE)), "20250815")
            this += XProperty(JtxICalObject.X_PROP_COMPLETEDTIMEZONE, JtxContract.JtxICalObject.TZ_ALLDAY)
        }

        builder.build(from = task, main = task, to = output)

        val expectedTimestamp = LocalDate.of(2025, 8, 15).toTimestamp()
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxICalObject.COMPLETED to expectedTimestamp,
                JtxContract.JtxICalObject.COMPLETED_TIMEZONE to JtxContract.JtxICalObject.TZ_ALLDAY
            ),
            output.entityValues
        )
    }
}
