/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.tasks.contract.TaskAlarms
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Repeat
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import kotlin.jvm.optionals.getOrNull

/**
 * RFC 5545 §3.6.6 VALARM, own table (not a [at.bitfire.tasks.contract.TaskProperties] mimetype).
 * Unlike the DMFS backend, which collapses every alarm to `minutes_before` + a START/DUE
 * reference (losing absolute triggers, DURATION/REPEAT and non-DISPLAY actions), this stores the
 * TRIGGER/DURATION/REPEAT/ACTION fields directly (§1 of the design doc).
 *
 * Alarm ATTENDEE/ATTACH ([at.bitfire.tasks.contract.AlarmProperties], EMAIL/AUDIO actions) are
 * not yet mapped (a known v1 gap, same as every other current task provider).
 */
class AlarmsBuilder(
    private val taskList: DavTaskList
) : DavTaskEntityBuilder {

    override fun build(from: VToDo, to: Entity) {
        for (alarm in from.alarms) {
            val action = when (alarm.getProperty<Action>(Property.ACTION).getOrNull()?.value?.uppercase()) {
                ImmutableAction.VALUE_AUDIO   -> TaskAlarms.Action.AUDIO
                ImmutableAction.VALUE_EMAIL   -> TaskAlarms.Action.EMAIL
                else                          -> TaskAlarms.Action.DISPLAY
            }

            var triggerRelative: String? = null
            var triggerRelated: String? = null
            var triggerAbsolute: Long? = null
            val trigger = alarm.getProperty<Trigger>(Property.TRIGGER).getOrNull()
            if (trigger?.duration != null) {
                triggerRelative = trigger.duration.toString()
                triggerRelated = when (trigger.getParameter<Related>(Parameter.RELATED).getOrNull()) {
                    Related.END -> TaskAlarms.Related.END
                    else        -> TaskAlarms.Related.START
                }
            } else if (trigger?.isAbsolute == true) {
                triggerAbsolute = trigger.normalizedDate().toTimestamp()
            }

            val duration = alarm.getProperty<Duration>(Property.DURATION).getOrNull()?.value
            val repeat = alarm.getProperty<Repeat>(Property.REPEAT).getOrNull()?.value
            val uid = alarm.getProperty<Uid>(Uid.UID).getOrNull()?.value

            to.addSubValue(
                taskList.tasksAlarmsUri(asSyncAdapter = false),
                contentValuesOf(
                    TaskAlarms.ACTION to action,
                    TaskAlarms.TRIGGER_RELATIVE to triggerRelative,
                    TaskAlarms.TRIGGER_RELATED to triggerRelated,
                    TaskAlarms.TRIGGER_ABSOLUTE to triggerAbsolute,
                    TaskAlarms.DURATION to duration,
                    TaskAlarms.REPEAT to repeat,
                    TaskAlarms.DESCRIPTION to alarm.description?.value,
                    TaskAlarms.SUMMARY to alarm.summary?.value,
                    TaskAlarms.UID to uid
                )
            )
        }
    }

}
