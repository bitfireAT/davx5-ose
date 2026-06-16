/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
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
import kotlin.jvm.optionals.getOrNull

class AttendeesBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        for (attendee in from.getProperties<Attendee>(Property.ATTENDEE)) {
            val values = buildAttendee(attendee)
            to.addSubValue(JtxContract.JtxAttendee.CONTENT_URI, values)
        }
    }

    private fun buildAttendee(attendee: Attendee): ContentValues {
        val calAddress = attendee.calAddress?.toString()
        val cn = attendee.getParameter<Cn>(Parameter.CN).getOrNull()?.value
        val delegatedTo = attendee.getParameter<DelegatedTo>(Parameter.DELEGATED_TO).getOrNull()?.value
        val delegatedFrom = attendee.getParameter<DelegatedFrom>(Parameter.DELEGATED_FROM).getOrNull()?.value
        val cuType = attendee.getParameter<CuType>(Parameter.CUTYPE).getOrNull()?.value
        val dir = attendee.getParameter<Dir>(Parameter.DIR).getOrNull()?.value
        val language = attendee.getParameter<Language>(Parameter.LANGUAGE).getOrNull()?.value
        val member = attendee.getParameter<Member>(Parameter.MEMBER).getOrNull()?.value
        val partStat = attendee.getParameter<PartStat>(Parameter.PARTSTAT).getOrNull()?.value
        val role = attendee.getParameter<Role>(Parameter.ROLE).getOrNull()?.value
        val rsvp = attendee.getParameter<Rsvp>(Parameter.RSVP).getOrNull()?.value?.toBoolean()
        val sentBy = attendee.getParameter<SentBy>(Parameter.SENT_BY).getOrNull()?.value

        val otherParameters = attendee.parameterList.removeAll(
            Parameter.CN,
            Parameter.DELEGATED_TO,
            Parameter.DELEGATED_FROM,
            Parameter.CUTYPE,
            Parameter.DIR,
            Parameter.LANGUAGE,
            Parameter.MEMBER,
            Parameter.PARTSTAT,
            Parameter.ROLE,
            Parameter.RSVP,
            Parameter.SENT_BY
        )
        val others = JtxContract.getJsonStringFromXParameters(otherParameters)

        return contentValuesOf(
            JtxContract.JtxAttendee.CALADDRESS to calAddress,
            JtxContract.JtxAttendee.CN to cn,
            JtxContract.JtxAttendee.DELEGATEDTO to delegatedTo,
            JtxContract.JtxAttendee.DELEGATEDFROM to delegatedFrom,
            JtxContract.JtxAttendee.CUTYPE to cuType,
            JtxContract.JtxAttendee.DIR to dir,
            JtxContract.JtxAttendee.LANGUAGE to language,
            JtxContract.JtxAttendee.MEMBER to member,
            JtxContract.JtxAttendee.PARTSTAT to partStat,
            JtxContract.JtxAttendee.ROLE to role,
            JtxContract.JtxAttendee.RSVP to rsvp,
            JtxContract.JtxAttendee.SENTBY to sentBy,
            JtxContract.JtxAttendee.OTHER to others
        )
    }
}
