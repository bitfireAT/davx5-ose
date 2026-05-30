/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.propertyListOf
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Trigger
import org.dmfs.tasks.contract.TaskContract.Property.Alarm

class AlarmsHandler : DmfsTaskFieldHandler {

    override fun process(from: ContentValues, to: Task) {
        val props = propertyListOf(
            Trigger(java.time.Duration.ofMinutes(-from.getAsLong(Alarm.MINUTES_BEFORE))).let {
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

}
