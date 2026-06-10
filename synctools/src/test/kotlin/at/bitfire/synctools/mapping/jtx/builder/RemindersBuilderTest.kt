/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Attach
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Repeat
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.XProperty
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class RemindersBuilderTest {

    private val builder = RemindersBuilder()

    @Test
    fun `alarm with trigger relative`() {
        val task = VToDo().apply {
            this += VAlarm().apply {
                this += ImmutableAction.AUDIO
                this += Attach(URI.create("https://domain.example/alarm.mp3"))
                this += Description("description")
                this += Duration("PT5M")
                this += Repeat(3)
                this += Summary("summary")
                this += Trigger(ParameterList(listOf(Related.START)), "-PT1H")
            }
        }
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertEquals(1, output.subValues.size)
        val alarmSubValue = output.subValues.first()
        assertEquals(JtxContract.JtxAlarm.CONTENT_URI, alarmSubValue.uri)
        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxAlarm.ACTION to "AUDIO",
                JtxContract.JtxAlarm.ATTACH to "https://domain.example/alarm.mp3",
                JtxContract.JtxAlarm.DESCRIPTION to "description",
                JtxContract.JtxAlarm.DURATION to "PT5M",
                JtxContract.JtxAlarm.REPEAT to 3,
                JtxContract.JtxAlarm.SUMMARY to "summary",
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO to "START",
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "PT-1H",
                JtxContract.JtxAlarm.TRIGGER_TIME to null,
                JtxContract.JtxAlarm.TRIGGER_TIMEZONE to null,
                JtxContract.JtxAlarm.OTHER to null,
            ),
            actual = alarmSubValue.values
        )
    }

    @Test
    fun `alarm with trigger time`() {
        val task = VToDo().apply {
            this += VAlarm().apply {
                this += ImmutableAction.DISPLAY
                this += Description("description")
                this += Trigger(Instant.parse("2026-05-19T12:00:00Z"))
            }
        }
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        val alarmSubValue = output.subValues.first()
        assertEquals(JtxContract.JtxAlarm.CONTENT_URI, alarmSubValue.uri)
        assertEquals("DISPLAY", alarmSubValue.values.getAsString(JtxContract.JtxAlarm.ACTION))
        assertEquals("description", alarmSubValue.values.getAsString(JtxContract.JtxAlarm.DESCRIPTION))
        assertEquals(1779192000000L, alarmSubValue.values.getAsLong(JtxContract.JtxAlarm.TRIGGER_TIME))
        assertEquals("Z", alarmSubValue.values.getAsString(JtxContract.JtxAlarm.TRIGGER_TIMEZONE))
    }

    @Test
    fun `multiple alarms`() {
        val task = VToDo().apply {
            this += VAlarm().apply {
                this += ImmutableAction.DISPLAY
                this += Description("description 1")
                this += Trigger(Instant.parse("2026-05-19T12:00:00Z"))
            }
            this += VAlarm().apply {
                this += ImmutableAction.DISPLAY
                this += Description("description 2")
                this += Trigger(ParameterList(listOf(Related.START)), "-PT1H")
            }
        }
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertEquals(2, output.subValues.size)
        val first = output.subValues[0]
        assertEquals(JtxContract.JtxAlarm.CONTENT_URI, first.uri)
        assertEquals("DISPLAY", first.values.getAsString(JtxContract.JtxAlarm.ACTION))
        assertEquals("description 1", first.values.getAsString(JtxContract.JtxAlarm.DESCRIPTION))
        assertEquals(1779192000000L, first.values.getAsLong(JtxContract.JtxAlarm.TRIGGER_TIME))
        assertEquals("Z", first.values.getAsString(JtxContract.JtxAlarm.TRIGGER_TIMEZONE))
        val second = output.subValues[1]
        assertEquals(JtxContract.JtxAlarm.CONTENT_URI, second.uri)
        assertEquals("DISPLAY", second.values.getAsString(JtxContract.JtxAlarm.ACTION))
        assertEquals("description 2", second.values.getAsString(JtxContract.JtxAlarm.DESCRIPTION))
        assertEquals("PT-1H", second.values.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION))
        assertEquals("START", second.values.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO))
    }

    @Test
    fun `alarm with custom properties`() {
        val task = VToDo().apply {
            this += VAlarm().apply {
                this += ImmutableAction.DISPLAY
                this += Description("description")
                this += Trigger(Instant.parse("2026-05-19T12:00:00Z"))
                this += XProperty("X-TEST-1", "test 1")
                this += XProperty("X-TEST-2", "test 2")
            }
        }
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        val alarmSubValue = output.subValues.first()
        assertEquals(JtxContract.JtxAlarm.CONTENT_URI, alarmSubValue.uri)
        assertEquals(
            """{"X-TEST-1":"test 1","X-TEST-2":"test 2"}""",
            alarmSubValue.values.getAsString(JtxContract.JtxAlarm.OTHER)
        )
    }
}
