/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.dtStart
import at.bitfire.synctools.icalendar.due
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.util.AlarmTriggerCalculator
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import org.dmfs.tasks.contract.TaskContract.Property.Alarm
import java.time.temporal.Temporal
import java.util.Locale
import kotlin.jvm.optionals.getOrNull

class AlarmsBuilder(
    private val taskList: DmfsTaskList
) : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        for (alarm in from.alarms) {
            val (alarmRef, minutes) = AlarmTriggerCalculator.alarmTriggerToMinutes(
                alarm = alarm,
                refStart = from.dtStart,
                refEnd = from.end,
                allowRelEnd = true
            ) ?: continue

            val ref = when (alarmRef) {
                Related.END ->
                    Alarm.ALARM_REFERENCE_DUE_DATE
                else /* Related.START is the default value */ ->
                    Alarm.ALARM_REFERENCE_START_DATE
            }

            val alarmType = when (
                alarm.getProperty<Action>(Property.ACTION).getOrNull()?.value?.uppercase(Locale.ROOT)
            ) {
                ImmutableAction.VALUE_AUDIO   -> Alarm.ALARM_TYPE_SOUND
                ImmutableAction.VALUE_DISPLAY -> Alarm.ALARM_TYPE_MESSAGE
                ImmutableAction.VALUE_EMAIL   -> Alarm.ALARM_TYPE_EMAIL
                else                          -> Alarm.ALARM_TYPE_NOTHING
            }

            to.addSubValue(
                taskList.tasksPropertiesUri(),
                contentValuesOf(
                    Alarm.MIMETYPE to Alarm.CONTENT_ITEM_TYPE,
                    Alarm.MINUTES_BEFORE to minutes,
                    Alarm.REFERENCE to ref,
                    Alarm.MESSAGE to (alarm.description?.value ?: alarm.summary),
                    Alarm.ALARM_TYPE to alarmType
                )
            )
        }
    }

    override fun build(from: VToDo, to: Entity) {
        for (alarm in from.alarms) {
            val (alarmRef, minutes) = AlarmTriggerCalculator.alarmTriggerToMinutes(
                alarm = alarm,
                refStart = from.dtStart<Temporal>(),
                refEnd = from.due<Temporal>(),
                allowRelEnd = true
            ) ?: continue

            val ref = when (alarmRef) {
                Related.END ->
                    Alarm.ALARM_REFERENCE_DUE_DATE
                else /* Related.START is the default value */ ->
                    Alarm.ALARM_REFERENCE_START_DATE
            }

            val alarmType = when (
                alarm.getProperty<Action>(Property.ACTION).getOrNull()?.value?.uppercase(Locale.ROOT)
            ) {
                ImmutableAction.VALUE_AUDIO   -> Alarm.ALARM_TYPE_SOUND
                ImmutableAction.VALUE_DISPLAY -> Alarm.ALARM_TYPE_MESSAGE
                ImmutableAction.VALUE_EMAIL   -> Alarm.ALARM_TYPE_EMAIL
                else                          -> Alarm.ALARM_TYPE_NOTHING
            }

            to.addSubValue(
                taskList.tasksPropertiesUri(),
                contentValuesOf(
                    Alarm.MIMETYPE to Alarm.CONTENT_ITEM_TYPE,
                    Alarm.MINUTES_BEFORE to minutes,
                    Alarm.REFERENCE to ref,
                    Alarm.MESSAGE to (alarm.description?.value ?: alarm.summary),
                    Alarm.ALARM_TYPE to alarmType
                )
            )
        }
    }

}
