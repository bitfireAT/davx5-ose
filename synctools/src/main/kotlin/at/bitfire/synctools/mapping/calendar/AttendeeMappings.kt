/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.property.Attendee
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

/**
 * Defines mappings between Android [Attendees] and iCalendar parameters.
 *
 * Because the available Android values are quite different from the one in iCalendar, the
 * mapping is very lossy. Some special mapping rules are defined:
 *
 *   - ROLE=CHAIR   ⇄ ATTENDEE_TYPE=TYPE_SPEAKER
 *   - CUTYPE=GROUP ⇄ ATTENDEE_TYPE=TYPE_PERFORMER
 *   - CUTYPE=ROOM  ⇄ ATTENDEE_TYPE=TYPE_RESOURCE, ATTENDEE_RELATIONSHIP=RELATIONSHIP_PERFORMER
 */
object AttendeeMappings {

    /**
     * Maps Android [Attendees.ATTENDEE_TYPE] and [Attendees.ATTENDEE_RELATIONSHIP] to
     * iCalendar [CuType] and [Role] according to this matrix:
     *
     *     TYPE ↓ / RELATIONSHIP → ATTENDEE¹  PERFORMER  SPEAKER   NONE
     *     REQUIRED                indᴰ,reqᴰ  gro,reqᴰ   indᴰ,cha  unk,reqᴰ
     *     OPTIONAL                indᴰ,opt   gro,opt    indᴰ,cha  unk,opt
     *     NONE                    indᴰ,reqᴰ  gro,reqᴰ   indᴰ,cha  unk,reqᴰ
     *     RESOURCE                res,reqᴰ   roo,reqᴰ   res,cha   res,reqᴰ
     *
     *     ᴰ default value
     *     ¹ includes ORGANIZER
     *
     * @param row        Android attendee row to map
     * @param attendee   iCalendar attendee to fill
     */
    fun androidToICalendar(row: ContentValues, attendee: Attendee) {
        val type = row.getAsInteger(Attendees.ATTENDEE_TYPE) ?: Attendees.TYPE_NONE
        val relationship = row.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP) ?: Attendees.RELATIONSHIP_NONE

        var cuType: CuType? = null
        val role: Role?

        if (relationship == Attendees.RELATIONSHIP_SPEAKER) {
            role = Role.CHAIR
            if (type == Attendees.TYPE_RESOURCE)
                cuType = CuType.RESOURCE

        } else /* relationship != Attendees.RELATIONSHIP_SPEAKER */ {

            cuType = when (relationship) {
                Attendees.RELATIONSHIP_PERFORMER -> CuType.GROUP
                Attendees.RELATIONSHIP_NONE -> CuType.UNKNOWN
                else -> CuType.INDIVIDUAL
            }

            when (type) {
                Attendees.TYPE_OPTIONAL -> role = Role.OPT_PARTICIPANT
                Attendees.TYPE_RESOURCE  -> {
                    cuType =
                            if (relationship == Attendees.RELATIONSHIP_PERFORMER)
                                CuType.ROOM
                            else
                                CuType.RESOURCE
                    role = Role.REQ_PARTICIPANT
                }
                else /* Attendees.TYPE_REQUIRED, Attendees.TYPE_NONE */ ->
                    role = Role.REQ_PARTICIPANT
            }

        }

        if (cuType != null && cuType != CuType.INDIVIDUAL)
            attendee += cuType
        if (role != null && role != Role.REQ_PARTICIPANT)
            attendee += role
    }


    /**
     * Maps iCalendar [CuType] and [Role] to Android [CalendarContract.AttendeesColumns.ATTENDEE_TYPE] and
     * [CalendarContract.AttendeesColumns.ATTENDEE_RELATIONSHIP] according to this matrix:
     *
     *     CuType ↓ / Role →   CHAIR    REQ-PARTICIPANT¹ᴰ OPT-PARTICIPANT  NON-PARTICIPANT
     *     INDIVIDUALᴰ         req,spk  req,att           opt,att          non,att
     *     UNKNOWN²            req,spk  req,non           opt,non          non,non
     *     GROUP               req,spk  req,per           opt,per          non,per
     *     RESOURCE            res,spk  res,non           res,non          res,non
     *     ROOM                ::: res,per ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
     *
     *     ᴰ default value
     *     ¹ custom/unknown ROLE values must be treated as REQ-PARTICIPANT
     *     ² custom/unknown CUTYPE values must be treated as UNKNOWN
     *
     *  When [attendee] is the [organizer], [CalendarContract.Attendees.ATTENDEE_RELATIONSHIP] = RELATIONSHIP_ATTENDEE
     *  is replaced by [CalendarContract.Attendees.RELATIONSHIP_ORGANIZER].
     *
     * @param attendee   iCalendar attendee to map
     * @param to         where to mapped values should be put into
     * @param organizer  email address of iCalendar ORGANIZER; used to determine whether [attendee] is the organizer
     */
    fun iCalendarToAndroid(attendee: Attendee, to: ContentValues, organizer: String) {
        val type: Int
        var relationship: Int

        val cuType = attendee.getParameter<CuType>(Parameter.CUTYPE).getOrDefault(CuType.INDIVIDUAL)
        val role = attendee.getParameter<Role>(Parameter.ROLE).getOrDefault(Role.REQ_PARTICIPANT)

        when (cuType) {
            CuType.RESOURCE -> {
                type = Attendees.TYPE_RESOURCE
                relationship =
                        if (role == Role.CHAIR)
                            Attendees.RELATIONSHIP_SPEAKER
                        else
                            Attendees.RELATIONSHIP_NONE
            }
            CuType.ROOM -> {
                type = Attendees.TYPE_RESOURCE
                relationship = Attendees.RELATIONSHIP_PERFORMER
            }

            else -> {
                // not a room and not a resource -> individual (default), group or unknown (includes x-custom)
                relationship = when (cuType) {
                    CuType.GROUP ->
                        Attendees.RELATIONSHIP_PERFORMER
                    CuType.UNKNOWN ->
                        Attendees.RELATIONSHIP_NONE
                    else -> /* CuType.INDIVIDUAL and custom/unknown values */
                        Attendees.RELATIONSHIP_ATTENDEE
                }

                when (role) {
                    Role.CHAIR -> {
                        type = Attendees.TYPE_REQUIRED
                        relationship = Attendees.RELATIONSHIP_SPEAKER
                    }
                    Role.OPT_PARTICIPANT ->
                        type = Attendees.TYPE_OPTIONAL
                    Role.NON_PARTICIPANT ->
                        type = Attendees.TYPE_NONE
                    else -> /* Role.REQ_PARTICIPANT and custom/unknown values */
                        type = Attendees.TYPE_REQUIRED
                }
            }
        }

        if (relationship == Attendees.RELATIONSHIP_ATTENDEE) {
            val uri = attendee.calAddress
            val email = if (uri.scheme.equals("mailto", true))
                uri.schemeSpecificPart
            else
                attendee.getParameter<Email>(Parameter.EMAIL).getOrNull()?.value

            if (email == organizer)
                relationship = Attendees.RELATIONSHIP_ORGANIZER
        }

        to.put(Attendees.ATTENDEE_TYPE, type)
        to.put(Attendees.ATTENDEE_RELATIONSHIP, relationship)
    }

}