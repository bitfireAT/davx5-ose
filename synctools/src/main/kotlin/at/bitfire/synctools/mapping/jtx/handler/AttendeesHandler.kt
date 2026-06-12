/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
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

class AttendeesHandler : JtxObjectEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(
        from: Entity,
        main: Entity,
        to: CalendarComponent
    ) {
        for (row in from.subValues.filter { it.uri == JtxContract.JtxAttendee.CONTENT_URI }) {
            populateAttendee(row.values, to)
        }
    }

    private fun populateAttendee(row: ContentValues, to: CalendarComponent) {
        val attendee = Attendee()
        row.getAsString(JtxContract.JtxAttendee.CALADDRESS)
            ?.takeIf { it.isNotEmpty() }
            ?.let { calAddress ->
                attendee.calAddress = try {
                    URI(calAddress)
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Ignoring invalid attendee URI: $calAddress", e)
                    return
                }
            }

        row.getAsString(JtxContract.JtxAttendee.CN)?.let { attendee += Cn(it) }
        row.getAsString(JtxContract.JtxAttendee.CUTYPE)?.let { attendee += cuType(it) }
        row.getAsString(JtxContract.JtxAttendee.DELEGATEDFROM)?.let { attendee += DelegatedFrom(it) }
        row.getAsString(JtxContract.JtxAttendee.DELEGATEDTO)?.let { attendee += DelegatedTo(it) }
        row.getAsString(JtxContract.JtxAttendee.DIR)?.let { attendee += Dir(it) }
        row.getAsString(JtxContract.JtxAttendee.LANGUAGE)?.let { attendee += Language(it) }
        row.getAsString(JtxContract.JtxAttendee.MEMBER)?.let { attendee += Member(it) }
        row.getAsString(JtxContract.JtxAttendee.PARTSTAT)?.let { attendee += PartStat(it) }
        row.getAsString(JtxContract.JtxAttendee.ROLE)?.let { attendee += Role(it) }
        row.getRsvp()?.let { attendee += Rsvp(it) }
        row.getAsString(JtxContract.JtxAttendee.SENTBY)?.let { attendee += SentBy(it) }
        row.getAsString(JtxContract.JtxAttendee.OTHER)?.let { other ->
            val otherParameters = JtxContract.getXParametersFromJson(other)
            for (parameter in otherParameters) {
                attendee += parameter
            }
        }

        to += attendee
    }

    private fun cuType(value: String): CuType = when {
        value.equals(CuType.INDIVIDUAL.value, ignoreCase = true) -> CuType.INDIVIDUAL
        value.equals(CuType.GROUP.value, ignoreCase = true) -> CuType.GROUP
        value.equals(CuType.ROOM.value, ignoreCase = true) -> CuType.ROOM
        value.equals(CuType.RESOURCE.value, ignoreCase = true) -> CuType.RESOURCE
        value.equals(CuType.UNKNOWN.value, ignoreCase = true) -> CuType.UNKNOWN
        else -> CuType.UNKNOWN
    }

    private fun ContentValues.getRsvp(): Boolean? =
        when (val value = get(JtxContract.JtxAttendee.RSVP)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is CharSequence -> value == "1" || value.toString().equals("true", ignoreCase = true)
            else -> null
        }
}
