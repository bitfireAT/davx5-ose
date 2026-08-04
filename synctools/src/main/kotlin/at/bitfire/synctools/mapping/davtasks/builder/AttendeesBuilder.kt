/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.DelegatedFrom
import net.fortuna.ical4j.model.parameter.DelegatedTo
import net.fortuna.ical4j.model.parameter.Dir
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.Member
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.parameter.SentBy
import net.fortuna.ical4j.model.property.Attendee
import kotlin.jvm.optionals.getOrNull

/**
 * RFC 5545 §3.8.4.1 ATTENDEE, full fidelity (unlike the DMFS backend, which maps to Android's
 * lossy ATTENDEE_TYPE/ATTENDEE_RELATIONSHIP enums) — all parameters are stored as raw strings.
 */
class AttendeesBuilder(
    private val taskList: DavTaskList
) : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        for (attendee in from.getProperties<Attendee>(Property.ATTENDEE)) {
            to.addSubValue(taskList.tasksPropertiesUri(asSyncAdapter = false), buildAttendee(attendee))
        }
    }

    private fun buildAttendee(attendee: Attendee) = contentValuesOf(
        TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_ATTENDEE,
        TaskProperties.DATA1 to attendee.calAddress?.toString(),
        TaskProperties.DATA2 to attendee.getParameter<Cn>(Parameter.CN).getOrNull()?.value,
        TaskProperties.DATA3 to attendee.getParameter<CuType>(Parameter.CUTYPE).getOrNull()?.value,
        TaskProperties.DATA4 to attendee.getParameter<DelegatedFrom>(Parameter.DELEGATED_FROM).getOrNull()?.value,
        TaskProperties.DATA5 to attendee.getParameter<DelegatedTo>(Parameter.DELEGATED_TO).getOrNull()?.value,
        TaskProperties.DATA6 to attendee.getParameter<Dir>(Parameter.DIR).getOrNull()?.value,
        TaskProperties.DATA7 to attendee.getParameter<Language>(Parameter.LANGUAGE).getOrNull()?.value,
        TaskProperties.DATA8 to attendee.getParameter<Member>(Parameter.MEMBER).getOrNull()?.value,
        TaskProperties.DATA9 to attendee.getParameter<PartStat>(Parameter.PARTSTAT).getOrNull()?.value,
        TaskProperties.DATA10 to attendee.getParameter<Role>(Parameter.ROLE).getOrNull()?.value,
        TaskProperties.DATA11 to attendee.getParameter<Rsvp>(Parameter.RSVP).getOrNull()?.value,
        TaskProperties.DATA12 to attendee.getParameter<SentBy>(Parameter.SENT_BY).getOrNull()?.value
    )
}
