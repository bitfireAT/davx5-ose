/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
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

class AlarmsHandler : DmfsTaskPropertyHandler {

    override fun process(row: ContentValues, to: Task) {
        val props = propertyListOf(
            Trigger(java.time.Duration.ofMinutes(-row.getAsLong(Alarm.MINUTES_BEFORE))).let {
                when (row.getAsInteger(Alarm.REFERENCE)) {
                    Alarm.ALARM_REFERENCE_START_DATE -> it.add(Related.START)
                    Alarm.ALARM_REFERENCE_DUE_DATE -> it.add(Related.END)
                    else -> it
                }
            },
            Action(
                when (row.getAsInteger(Alarm.ALARM_TYPE)) {
                    Alarm.ALARM_TYPE_EMAIL -> Action.VALUE_EMAIL
                    Alarm.ALARM_TYPE_SOUND -> Action.VALUE_AUDIO
                    // show alarm by default
                    else -> Action.VALUE_DISPLAY
                }
            ),
            Description(row.getAsString(Alarm.MESSAGE) ?: to.summary)
        )

        to.alarms += VAlarm(props)
    }

}
