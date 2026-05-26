/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.handler

import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Action
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
        val task = Task()
        handler.process(contentValuesOf(
            Alarm.MINUTES_BEFORE to 15L,
            Alarm.REFERENCE to Alarm.ALARM_REFERENCE_START_DATE,
            Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_MESSAGE,
        ), task)

        assertEquals(1, task.alarms.size)
        val alarm = task.alarms.first()
        assertEquals(ImmutableAction.DISPLAY, alarm.actionProperty)
        assertEquals(Duration.ofMinutes(-15), alarm.triggerProperty.duration)
    }

    @Test
    fun `Audio alarm relative to due`() {
        val task = Task()
        handler.process(contentValuesOf(
            Alarm.MINUTES_BEFORE to 10L,
            Alarm.REFERENCE to Alarm.ALARM_REFERENCE_DUE_DATE,
            Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_SOUND,
        ), task)

        assertEquals(1, task.alarms.size)
        val alarm = task.alarms.first()
        assertEquals(ImmutableAction.AUDIO, alarm.actionProperty)
        assertEquals(Duration.ofMinutes(-10), alarm.triggerProperty.duration)
        // Related.END parameter should be set for due-relative alarms
        assertTrue(alarm.triggerProperty.getParameter<net.fortuna.ical4j.model.parameter.Related>(
            net.fortuna.ical4j.model.Parameter.RELATED
        ).isPresent)
    }

    @Test
    fun `Email alarm`() {
        val task = Task()
        handler.process(contentValuesOf(
            Alarm.MINUTES_BEFORE to 5L,
            Alarm.REFERENCE to Alarm.ALARM_REFERENCE_START_DATE,
            Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_EMAIL,
        ), task)

        assertEquals(1, task.alarms.size)
        assertEquals(ImmutableAction.EMAIL, task.alarms.first().actionProperty)
    }

    @Test
    fun `Alarm message is used as description`() {
        val task = Task()
        handler.process(contentValuesOf(
            Alarm.MINUTES_BEFORE to 0L,
            Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_MESSAGE,
            Alarm.MESSAGE to "Don't forget!",
        ), task)

        assertEquals("Don't forget!", task.alarms.first().description?.value)
    }

    @Test
    fun `Task summary used as description when no alarm message`() {
        val task = Task(summary = "Task Title")
        handler.process(contentValuesOf(
            Alarm.MINUTES_BEFORE to 0L,
            Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_MESSAGE,
        ), task)

        assertEquals("Task Title", task.alarms.first().description?.value)
    }

    @Test
    fun `Multiple alarms accumulate`() {
        val task = Task()
        handler.process(contentValuesOf(
            Alarm.MINUTES_BEFORE to 10L,
            Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_MESSAGE,
        ), task)
        handler.process(contentValuesOf(
            Alarm.MINUTES_BEFORE to 20L,
            Alarm.ALARM_TYPE to Alarm.ALARM_TYPE_SOUND,
        ), task)

        assertEquals(2, task.alarms.size)
    }

}

private val VAlarm.actionProperty: Action?
    get() = getProperty<Action>(Property.ACTION).getOrNull()

private val VAlarm.triggerProperty: Trigger
    get() = getProperty<Trigger>(Property.TRIGGER).get()
