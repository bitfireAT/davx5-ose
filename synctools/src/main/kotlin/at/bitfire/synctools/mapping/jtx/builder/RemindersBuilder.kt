/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.mapping.jtx.builder.TimeZoneIdMapper.toTimeZoneId
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attach
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Repeat
import net.fortuna.ical4j.model.property.Trigger
import kotlin.jvm.optionals.getOrNull

class RemindersBuilder : JtxObjectEntityBuilder {

    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        for (alarm in getAlarms(from)) {
            val reminderValues = buildReminder(alarm)
            to.addSubValue(JtxContract.JtxAlarm.CONTENT_URI, reminderValues)
        }
    }

    private fun buildReminder(alarm: VAlarm): ContentValues {
        val action = alarm.getProperty<Action>(Property.ACTION).getOrNull()?.value
        val summary = alarm.summary?.value
        val description = alarm.description?.value
        val duration = alarm.getProperty<Duration>(Property.DURATION).getOrNull()?.value
        val attach = alarm.getProperty<Attach>(Property.ATTACH).getOrNull()?.uri?.toString()
        val repeat = alarm.getProperty<Repeat>(Property.REPEAT).getOrNull()?.value

        var triggerRelativeDuration: String? = null
        var triggerRelativeTo: String? = null
        var triggerTime: Long? = null
        var triggerTimezone: String? = null
        val trigger = alarm.getProperty<Trigger>(Property.TRIGGER).getOrNull()
        // Alarms can have a duration or an absolute dateTime, but not both!
        if (trigger?.duration != null) {
            triggerRelativeDuration = trigger.duration.toString()
            triggerRelativeTo = trigger.getParameter<Parameter>(Parameter.RELATED).getOrNull()?.value
        } else if (trigger?.isAbsolute == true) {
            val normalizedTriggerDate = trigger.normalizedDate()
            triggerTime = normalizedTriggerDate.toTimestamp()
            triggerTimezone = normalizedTriggerDate.toTimeZoneId()
        }

        val otherProperties = alarm.propertyList.removeAll(
            Property.ACTION,
            Property.SUMMARY,
            Property.DESCRIPTION,
            Property.DURATION,
            Property.ATTACH,
            Property.REPEAT,
            Property.TRIGGER
        )
        val other = JtxContract.getJsonStringFromXProperties(otherProperties)

        return contentValuesOf(
            JtxContract.JtxAlarm.ACTION to action,
            JtxContract.JtxAlarm.ATTACH to attach,
            JtxContract.JtxAlarm.DESCRIPTION to description,
            JtxContract.JtxAlarm.DURATION to duration,
            JtxContract.JtxAlarm.REPEAT to repeat,
            JtxContract.JtxAlarm.SUMMARY to summary,
            JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO to triggerRelativeTo,
            JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION to triggerRelativeDuration,
            JtxContract.JtxAlarm.TRIGGER_TIME to triggerTime,
            JtxContract.JtxAlarm.TRIGGER_TIMEZONE to triggerTimezone,
            JtxContract.JtxAlarm.OTHER to other
        )
    }

    private fun getAlarms(component: CalendarComponent): List<VAlarm> {
        val componentList = when (component) {
            is VToDo -> component.componentList
            is VJournal -> component.componentList
            else -> error("Unsupported calendar component: ${component::class.simpleName}")
        }

        return componentList.all.filterIsInstance<VAlarm>()
    }
}
