/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.mapping.tasks.mimeType
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Trigger
import org.dmfs.tasks.contract.TaskContract.Property.Alarm
import java.time.Duration
import kotlin.jvm.optionals.getOrNull

class AlarmsHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        val props = propertyListOf(
            Trigger(Duration.ofMinutes(-from.getAsLong(Alarm.MINUTES_BEFORE))).let {
                when (from.getAsInteger(Alarm.REFERENCE)) {
                    Alarm.ALARM_REFERENCE_START_DATE -> it.add(Related.START)
                    Alarm.ALARM_REFERENCE_DUE_DATE -> it.add(Related.END)
                    else -> it
                }
            },
            Action(
                when (from.getAsInteger(Alarm.ALARM_TYPE)) {
                    Alarm.ALARM_TYPE_EMAIL -> Action.VALUE_EMAIL
                    Alarm.ALARM_TYPE_SOUND -> Action.VALUE_AUDIO
                    // show alarm by default
                    else -> Action.VALUE_DISPLAY
                }
            ),
            Description(from.getAsString(Alarm.MESSAGE) ?: to.summary)
        )

        to.alarms += VAlarm(props)
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val summary = to.getProperty<Summary>(Property.SUMMARY).getOrNull()?.value
        for (row in from.subValues.filter { it.mimeType == Alarm.CONTENT_ITEM_TYPE }) {
            processAlarm(row.values, summary, to)
        }
    }

    private fun processAlarm(values: ContentValues, summary: String?, to: VToDo) {
        val props = propertyListOf(
            Trigger(Duration.ofMinutes(-values.getAsLong(Alarm.MINUTES_BEFORE))).let {
                when (values.getAsInteger(Alarm.REFERENCE)) {
                    Alarm.ALARM_REFERENCE_START_DATE -> it.add(Related.START)
                    Alarm.ALARM_REFERENCE_DUE_DATE -> it.add(Related.END)
                    else -> it
                }
            },
            Action(
                when (values.getAsInteger(Alarm.ALARM_TYPE)) {
                    Alarm.ALARM_TYPE_EMAIL -> Action.VALUE_EMAIL
                    Alarm.ALARM_TYPE_SOUND -> Action.VALUE_AUDIO
                    // show alarm by default
                    else -> Action.VALUE_DISPLAY
                }
            ),
            Description(values.getAsString(Alarm.MESSAGE) ?: summary)
        )

        to += VAlarm(props)
    }

}
