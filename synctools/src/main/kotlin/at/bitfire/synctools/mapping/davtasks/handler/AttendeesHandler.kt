/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.davtasks.mimeType
import at.bitfire.tasks.contract.TaskProperties
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
import java.net.URI
import java.util.logging.Level
import java.util.logging.Logger

class AttendeesHandler : DavTaskEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VToDo) {
        for (row in from.subValues.filter { it.mimeType == TaskProperties.MIMETYPE_ATTENDEE }) {
            val calAddress = row.values.getAsString(TaskProperties.DATA1) ?: continue
            val attendee = Attendee()
            try {
                attendee.calAddress = URI(calAddress)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Ignoring invalid attendee URI: $calAddress", e)
                continue
            }

            row.values.getAsString(TaskProperties.DATA2)?.let { attendee += Cn(it) }
            row.values.getAsString(TaskProperties.DATA3)?.let { attendee += CuType(it) }
            row.values.getAsString(TaskProperties.DATA4)?.let { attendee += DelegatedFrom(it) }
            row.values.getAsString(TaskProperties.DATA5)?.let { attendee += DelegatedTo(it) }
            row.values.getAsString(TaskProperties.DATA6)?.let { attendee += Dir(it) }
            row.values.getAsString(TaskProperties.DATA7)?.let { attendee += Language(it) }
            row.values.getAsString(TaskProperties.DATA8)?.let { attendee += Member(it) }
            row.values.getAsString(TaskProperties.DATA9)?.let { attendee += PartStat(it) }
            row.values.getAsString(TaskProperties.DATA10)?.let { attendee += Role(it) }
            row.values.getAsString(TaskProperties.DATA11)?.let { attendee += Rsvp(it) }
            row.values.getAsString(TaskProperties.DATA12)?.let { attendee += SentBy(it) }

            to += attendee
        }
    }
}
