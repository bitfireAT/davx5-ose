/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import org.dmfs.tasks.contract.TaskContract.Property.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class AlarmsHandlerTest {

    private val handler = AlarmsHandler()


    @Test
    fun `Display alarm relative to start`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Alarm.MIMETYPE to Alarm.CONTENT_ITEM_TYPE,
                Alarm.MINUTES_BEFORE to 15L,
                Alarm.REFERENCE to Alarm.ALARM_REFERENCE_START_DATE,
                Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_MESSAGE,
            ))
        }
        handler.process(from = input, main = input, to = vToDo)

        assertEquals(1, vToDo.alarms.size)
        val alarm = vToDo.alarms.first()
        assertEquals(ImmutableAction.DISPLAY, alarm.actionProperty)
        assertEquals(Duration.ofMinutes(-15), alarm.triggerProperty.duration)
    }

    @Test
    fun `Audio alarm relative to due`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Alarm.MIMETYPE to Alarm.CONTENT_ITEM_TYPE,
                Alarm.MINUTES_BEFORE to 10L,
                Alarm.REFERENCE to Alarm.ALARM_REFERENCE_DUE_DATE,
                Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_SOUND,
            ))
        }
        handler.process(from = input, main = input, to = vToDo)

        assertEquals(1, vToDo.alarms.size)
        val alarm = vToDo.alarms.first()
        assertEquals(ImmutableAction.AUDIO, alarm.actionProperty)
        assertEquals(Duration.ofMinutes(-10), alarm.triggerProperty.duration)
        // Related.END parameter should be set for due-relative alarms
        assertTrue(alarm.triggerProperty.getParameter<net.fortuna.ical4j.model.parameter.Related>(
            net.fortuna.ical4j.model.Parameter.RELATED
        ).isPresent)
    }

    @Test
    fun `Email alarm`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Alarm.MIMETYPE to Alarm.CONTENT_ITEM_TYPE,
                Alarm.MINUTES_BEFORE to 5L,
                Alarm.REFERENCE to Alarm.ALARM_REFERENCE_START_DATE,
                Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_EMAIL,
            ))
        }
        handler.process(from = input, main = input, to = vToDo)

        assertEquals(1, vToDo.alarms.size)
        assertEquals(ImmutableAction.EMAIL, vToDo.alarms.first().actionProperty)
    }

    @Test
    fun `Alarm message is used as description`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Alarm.MIMETYPE to Alarm.CONTENT_ITEM_TYPE,
                Alarm.MINUTES_BEFORE to 0L,
                Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_MESSAGE,
                Alarm.MESSAGE to "Don't forget!",
            ))
        }
        handler.process(from = input, main = input, to = vToDo)

        assertEquals(Description("Don't forget!"), vToDo.alarms.first().description)
    }

    @Test
    fun `Task summary used as description when no alarm message`() {
        val vToDo = VToDo()
        vToDo += Summary("Task Title")
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Alarm.MIMETYPE to Alarm.CONTENT_ITEM_TYPE,
                Alarm.MINUTES_BEFORE to 0L,
                Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_MESSAGE,
            ))
        }
        handler.process(from = input, main = input, to = vToDo)

        assertEquals(Description("Task Title"), vToDo.alarms.first().description)
    }

    @Test
    fun `Multiple alarms accumulate`() {
        val vToDo = VToDo()
        val input = Entity(ContentValues()).apply {
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Alarm.MIMETYPE to Alarm.CONTENT_ITEM_TYPE,
                Alarm.MINUTES_BEFORE to 10L,
                Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_MESSAGE,
            ))
            addSubValue(Uri.parse("irrelevant:"), contentValuesOf(
                Alarm.MIMETYPE to Alarm.CONTENT_ITEM_TYPE,
                Alarm.MINUTES_BEFORE to 20L,
                Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_SOUND,
            ))
        }
        handler.process(from = input, main = input, to = vToDo)

        assertEquals(2, vToDo.alarms.size)
    }

}

private val VAlarm.actionProperty: Action?
    get() = getProperty<Action>(Property.ACTION).getOrNull()

private val VAlarm.triggerProperty: Trigger
    get() = getProperty<Trigger>(Property.TRIGGER).get()
