/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.dtStart
import at.bitfire.synctools.util.AlarmTriggerCalculator
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Action
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull

class RemindersBuilder : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        for (reminder in from.alarms)
            to.addSubValue(Reminders.CONTENT_URI, buildReminder(reminder, from))
    }

    private fun buildReminder(alarm: VAlarm, event: VEvent): ContentValues {
        val method = when (alarm.getProperty<Action>(Property.ACTION)?.getOrNull()?.value?.uppercase()) {
            Action.VALUE_DISPLAY,
            Action.VALUE_AUDIO -> Reminders.METHOD_ALERT    // will trigger an alarm on the Android device

            // Note: The calendar provider doesn't support saving specific attendees for email reminders.
            Action.VALUE_EMAIL -> Reminders.METHOD_EMAIL

            else -> Reminders.METHOD_DEFAULT                // won't trigger an alarm on the Android device
        }

        val minutes = AlarmTriggerCalculator.alarmTriggerToMinutes(
            alarm = alarm,
            refStart = event.dtStart<Temporal>(),
            refEnd = event.getEndDate<Temporal>(true).getOrNull(),
            allowRelEnd = false
        )?.second ?: Reminders.MINUTES_DEFAULT

        return contentValuesOf(
            Reminders.METHOD to method,
            Reminders.MINUTES to minutes
        )
    }

}