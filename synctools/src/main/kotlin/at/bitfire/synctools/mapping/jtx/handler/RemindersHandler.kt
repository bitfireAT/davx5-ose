/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.ComponentContainer
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attach
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Repeat
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Trigger
import org.json.JSONException
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeParseException

class RemindersHandler : JtxObjectEntityHandler {
    @Suppress("UNCHECKED_CAST")
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        from.subValues.filter { it.uri == JtxContract.JtxAlarm.CONTENT_URI }.forEach { reminder ->
            (to as ComponentContainer<Component>) += decodeVAlarm(reminder.values)
        }
    }

    private fun decodeVAlarm(values: ContentValues): VAlarm {
        val alarmProps = mutableListOf<Property>()

        values.getAsString(JtxContract.JtxAlarm.ACTION)?.let { action ->
            when (action.uppercase()) {
                JtxContract.JtxAlarm.AlarmAction.DISPLAY.name -> alarmProps += Action(Action.VALUE_DISPLAY)
                JtxContract.JtxAlarm.AlarmAction.AUDIO.name -> alarmProps += Action(Action.VALUE_AUDIO)
                JtxContract.JtxAlarm.AlarmAction.EMAIL.name -> alarmProps += Action(Action.VALUE_EMAIL)
            }
        }
        values.getAsString(JtxContract.JtxAlarm.ATTACH)?.let { attach ->
            val url = try {
                URI.create(attach)
            } catch (_: IllegalArgumentException) {
                return@let
            }
            alarmProps += Attach(url)
        }
        values.getAsString(JtxContract.JtxAlarm.DESCRIPTION)?.let { description ->
            alarmProps += Description(description)
        }
        values.getAsString(JtxContract.JtxAlarm.DURATION)?.let { duration ->
            alarmProps += Duration(duration)
        }
        values.getAsString(JtxContract.JtxAlarm.REPEAT)?.let { repeat ->
            alarmProps += Repeat(ParameterList(), repeat)
        }
        values.getAsString(JtxContract.JtxAlarm.SUMMARY)?.let { summary ->
            alarmProps += Summary(summary)
        }

        // Add all the unknown properties
        values.getAsString(JtxContract.JtxAlarm.OTHER)
            ?.let { json ->
                try {
                    JtxContract.getXPropertyListFromJson(json)
                } catch (_: JSONException) {
                    null
                }
            }
            ?.let { alarmProps += it.all }

        handleTrigger(values)?.let { trigger ->
            alarmProps += trigger
        }

        return VAlarm(PropertyList(alarmProps))
    }

    private fun handleTrigger(values: ContentValues): Trigger? {
        val triggerTime: Long? = values.getAsLong(JtxContract.JtxAlarm.TRIGGER_TIME)
        val triggerRelativeDuration: String? = values.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION)
        val triggerRelativeTo: String? = values.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO)

        if (triggerRelativeDuration != null) {
            val duration = try {
                java.time.Duration.parse(triggerRelativeDuration)
            } catch (_: DateTimeParseException) {
                null
            } ?: return triggerTime?.let { Trigger(Instant.ofEpochMilli(it)) }

            return Trigger().apply {
                this.duration = duration
                when (triggerRelativeTo?.uppercase()) {
                    JtxContract.JtxAlarm.AlarmRelativeTo.END.name -> this += Related.END
                    // START is the default if RELATED is absent/invalid
                }
            }
        }

        if (triggerTime != null) {
            // TRIGGER with absolute date-time is always UTC per RFC 5545; timezone is ignored
            return Trigger(Instant.ofEpochMilli(triggerTime))
        }

        return null
    }
}
