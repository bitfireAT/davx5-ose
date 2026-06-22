/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attach
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Repeat
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class RemindersHandlerTest {

    private val handler = RemindersHandler()

    @Test
    fun `No alarm sub-values`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.alarms.size)
    }

    @Test
    fun `Sub-values with a different URI are ignored`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            Uri.parse("content://at.techbee.jtx/other"),
            contentValuesOf(JtxContract.JtxAlarm.ACTION to "DISPLAY")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.alarms.size)
    }

    @Test
    fun `Alarm with ACTION`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxAlarm.ACTION to "DISPLAY",
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT15M"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val alarm = output.alarms.first()
        assertEquals("DISPLAY", alarm.actionProperty?.value)
    }

    @Test
    fun `Alarm with ATTACH`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxAlarm.ATTACH to "https://example.com/sound.wav",
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT5M"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val alarm = output.alarms.first()
        val attach = alarm.getProperty<Attach>(Property.ATTACH).getOrNull()
        assertEquals("https://example.com/sound.wav", attach?.uri?.toString())
    }

    @Test
    fun `Alarm with invalid ATTACH is skipped`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxAlarm.ATTACH to "not a valid uri with spaces",
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT5M"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val alarm = output.alarms.first()
        assertNull(alarm.getProperty<Attach>(Property.ATTACH).getOrNull())
    }

    @Test
    fun `Alarm with DESCRIPTION`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxAlarm.DESCRIPTION to "Don't forget!",
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT5M"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val alarm = output.alarms.first()
        assertEquals("Don't forget!", alarm.getProperty<Description>(Property.DESCRIPTION).getOrNull()?.value)
    }

    @Test
    fun `Alarm with DURATION`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxAlarm.DURATION to "PT1H",
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT5M"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val alarm = output.alarms.first()
        assertNotNull(alarm.getProperty<Duration>(Property.DURATION).getOrNull())
    }

    @Test
    fun `Alarm with REPEAT`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxAlarm.REPEAT to "3",
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT5M"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val alarm = output.alarms.first()
        assertEquals("3", alarm.getProperty<Repeat>(Property.REPEAT).getOrNull()?.value)
    }

    @Test
    fun `Alarm with SUMMARY`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxAlarm.SUMMARY to "Reminder",
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT5M"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val alarm = output.alarms.first()
        assertEquals("Reminder", alarm.getProperty<Summary>(Property.SUMMARY).getOrNull()?.value)
    }

    @Test
    fun `Alarm with unknown properties in OTHER`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxAlarm.OTHER to """{"X-CUSTOM-PROP":"custom-value"}""",
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT5M"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val alarm = output.alarms.first()
        assertNotNull(alarm.getProperty<net.fortuna.ical4j.model.property.XProperty>("X-CUSTOM-PROP").getOrNull())
    }

    @Test
    fun `Trigger with relative duration`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT15M")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val trigger = output.alarms.first().triggerProperty!!
        assertEquals(java.time.Duration.ofMinutes(-15), trigger.duration)
        assertNull(trigger.getParameter<Related>(Parameter.RELATED).getOrNull())
    }

    @Test
    fun `Trigger with relative duration and RELATED to END`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT5M",
                JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO to "END"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val trigger = output.alarms.first().triggerProperty!!
        assertEquals(java.time.Duration.ofMinutes(-5), trigger.duration)
        assertEquals("END", trigger.getParameter<Related>(Parameter.RELATED).getOrNull()?.value)
    }

    @Test
    fun `Trigger with absolute time`() {
        val epochMs = 1_700_000_000_000L
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(JtxContract.JtxAlarm.TRIGGER_TIME to epochMs)
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val trigger = output.alarms.first().triggerProperty!!
        assertEquals(Instant.ofEpochMilli(epochMs), trigger.date)
    }

    @Test
    fun `Trigger with absolute time and TRIGGER_TIMEZONE is ignored (UTC property)`() {
        val epochMs = 1_700_000_000_000L
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(
                JtxContract.JtxAlarm.TRIGGER_TIME to epochMs,
                JtxContract.JtxAlarm.TRIGGER_TIMEZONE to "Europe/Vienna"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        // TRIGGER is always UTC per RFC 5545; timezone field has no effect
        val trigger = output.alarms.first().triggerProperty!!
        assertEquals(Instant.ofEpochMilli(epochMs), trigger.date)
    }

    @Test
    fun `No trigger when neither duration nor time is set`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(JtxContract.JtxAlarm.ACTION to "DISPLAY")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.alarms.first().triggerProperty)
    }

    @Test
    fun `Multiple alarms`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT15M")
        )
        input.addSubValue(
            JtxContract.JtxAlarm.CONTENT_URI,
            contentValuesOf(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to "-PT30M")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(2, output.alarms.size)
    }
}

private val VAlarm.actionProperty: Action?
    get() = getProperty<Action>(Property.ACTION).getOrNull()

private val VAlarm.triggerProperty: Trigger?
    get() = getProperty<Trigger>(Property.TRIGGER).getOrNull()
