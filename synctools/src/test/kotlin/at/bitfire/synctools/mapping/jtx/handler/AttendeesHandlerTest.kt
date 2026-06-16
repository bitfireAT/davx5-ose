/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import android.net.Uri
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
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
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Attendee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class AttendeesHandlerTest {

    private val handler = AttendeesHandler()

    @Test
    fun `No attendee sub-values`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.attendees.size)
    }

    @Test
    fun `Sub-values with a different URI are ignored`() {
        val input = Entity(ContentValues())
        input.addSubValue(
            Uri.parse("content://at.techbee.jtx/other"),
            contentValuesOf(JtxContract.JtxAttendee.CALADDRESS to "mailto:attendee@domain.example")
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.attendees.size)
    }

    @Test
    fun `Attendee calendar address`() {
        val input = Entity(ContentValues()).apply {
            addAttendee(JtxContract.JtxAttendee.CALADDRESS to "mailto:attendee@domain.example")
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(URI("mailto:attendee@domain.example"), output.firstAttendee.calAddress)
    }

    @Test
    fun `Invalid attendee calendar address is skipped`() {
        val input = Entity(ContentValues()).apply {
            addAttendee(JtxContract.JtxAttendee.CALADDRESS to "not a valid uri")
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(0, output.attendees.size)
    }

    @Test
    fun `Attendee without calendar address is mapped`() {
        val input = Entity(ContentValues()).apply {
            addAttendee(JtxContract.JtxAttendee.CN to "Jane Attendee")
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.firstAttendee.calAddress)
        assertEquals("Jane Attendee", output.firstAttendee.cn?.value)
    }

    @Test
    fun `VJOURNAL attendee is mapped`() {
        val input = Entity(ContentValues()).apply {
            addAttendee(JtxContract.JtxAttendee.CALADDRESS to "mailto:attendee@domain.example")
        }
        val output = VJournal()

        handler.process(from = input, main = input, to = output)

        assertEquals(URI("mailto:attendee@domain.example"), output.firstAttendee.calAddress)
    }

    @Test
    fun `Single attendee with all known and unknown parameters`() {
        val input = Entity(ContentValues()).apply {
            addAttendee(
                JtxContract.JtxAttendee.CALADDRESS to "mailto:attendee@domain.example",
                JtxContract.JtxAttendee.CN to "Jane Attendee",
                JtxContract.JtxAttendee.DELEGATEDTO to "\"mailto:delegated-to@domain.example\"",
                JtxContract.JtxAttendee.DELEGATEDFROM to "\"mailto:delegated-from@domain.example\"",
                JtxContract.JtxAttendee.CUTYPE to "INDIVIDUAL",
                JtxContract.JtxAttendee.DIR to "ldap://ldap.domain.example/something",
                JtxContract.JtxAttendee.LANGUAGE to "en-US",
                JtxContract.JtxAttendee.MEMBER to "\"mailto:list@domain.example\"",
                JtxContract.JtxAttendee.PARTSTAT to "ACCEPTED",
                JtxContract.JtxAttendee.ROLE to "REQ-PARTICIPANT",
                JtxContract.JtxAttendee.RSVP to false,
                JtxContract.JtxAttendee.SENTBY to "mailto:sent-by@domain.example",
                JtxContract.JtxAttendee.OTHER to """{"X-CUSTOM":"custom-value"}"""
            )
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        val attendee = output.firstAttendee
        assertEquals(URI("mailto:attendee@domain.example"), attendee.calAddress)
        assertEquals("Jane Attendee", attendee.cn?.value)
        assertEquals("\"mailto:delegated-to@domain.example\"", attendee.delegatedTo?.value)
        assertEquals("\"mailto:delegated-from@domain.example\"", attendee.delegatedFrom?.value)
        assertEquals(CuType.INDIVIDUAL, attendee.cuType)
        assertEquals("ldap://ldap.domain.example/something", attendee.dir?.value)
        assertEquals("en-US", attendee.language?.value)
        assertEquals("\"mailto:list@domain.example\"", attendee.member?.value)
        assertEquals(PartStat.ACCEPTED, attendee.partStat)
        assertEquals(Role.REQ_PARTICIPANT, attendee.role)
        assertFalse(attendee.rsvp?.rsvp ?: true)
        assertEquals("mailto:sent-by@domain.example", attendee.sentBy?.value)
        assertEquals("custom-value", attendee.getParameter<XParameter>("X-CUSTOM").getOrNull()?.value)
    }

    @Test
    fun `Multiple attendees`() {
        val input = Entity(ContentValues()).apply {
            addAttendee(JtxContract.JtxAttendee.CALADDRESS to "mailto:one@domain.example")
            addAttendee(JtxContract.JtxAttendee.CALADDRESS to "mailto:two@domain.example")
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(2, output.attendees.size)
        assertEquals(URI("mailto:one@domain.example"), output.attendees[0].calAddress)
        assertEquals(URI("mailto:two@domain.example"), output.attendees[1].calAddress)
    }

    @Test
    fun `Known CUTYPE values are mapped case-insensitively`() {
        val expected = mapOf(
            "individual" to CuType.INDIVIDUAL,
            "group" to CuType.GROUP,
            "room" to CuType.ROOM,
            "resource" to CuType.RESOURCE,
            "unknown" to CuType.UNKNOWN
        )

        for ((value, cuType) in expected) {
            val input = Entity(ContentValues()).apply {
                addAttendee(
                    JtxContract.JtxAttendee.CALADDRESS to "mailto:attendee@domain.example",
                    JtxContract.JtxAttendee.CUTYPE to value
                )
            }
            val output = VToDo()

            handler.process(from = input, main = input, to = output)

            assertEquals(cuType, output.firstAttendee.cuType)
        }
    }

    @Test
    fun `Unknown CUTYPE value is mapped to UNKNOWN`() {
        val input = Entity(ContentValues()).apply {
            addAttendee(
                JtxContract.JtxAttendee.CALADDRESS to "mailto:attendee@domain.example",
                JtxContract.JtxAttendee.CUTYPE to "unsupported"
            )
        }
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(CuType.UNKNOWN, output.firstAttendee.cuType)
    }

    @Test
    fun `RSVP true values`() {
        for (rsvpValue in arrayOf<Any>(true, 1, "1", "true", "TRUE")) {
            val input = Entity(ContentValues()).apply {
                addAttendee(
                    JtxContract.JtxAttendee.CALADDRESS to "mailto:attendee@domain.example",
                    JtxContract.JtxAttendee.RSVP to rsvpValue
                )
            }
            val output = VToDo()

            handler.process(from = input, main = input, to = output)

            assertTrue(output.firstAttendee.rsvp?.rsvp ?: false)
        }
    }

    @Test
    fun `RSVP false values`() {
        for (rsvpValue in arrayOf<Any>(false, 0, "0", "false", "FALSE")) {
            val input = Entity(ContentValues()).apply {
                addAttendee(
                    JtxContract.JtxAttendee.CALADDRESS to "mailto:attendee@domain.example",
                    JtxContract.JtxAttendee.RSVP to rsvpValue
                )
            }
            val output = VToDo()

            handler.process(from = input, main = input, to = output)

            assertFalse(output.firstAttendee.rsvp?.rsvp ?: true)
        }
    }
}

private fun Entity.addAttendee(vararg values: Pair<String, Any?>) {
    addSubValue(JtxContract.JtxAttendee.CONTENT_URI, contentValuesOf(*values))
}

private val CalendarComponent.attendees
    get() = getProperties<Attendee>(Property.ATTENDEE)

private val CalendarComponent.firstAttendee: Attendee
    get() = attendees.first()

private val Attendee.cn: Cn?
    get() = getParameter<Cn>(Parameter.CN).getOrNull()

private val Attendee.cuType: CuType?
    get() = getParameter<CuType>(Parameter.CUTYPE).getOrNull()

private val Attendee.delegatedFrom: DelegatedFrom?
    get() = getParameter<DelegatedFrom>(Parameter.DELEGATED_FROM).getOrNull()

private val Attendee.delegatedTo: DelegatedTo?
    get() = getParameter<DelegatedTo>(Parameter.DELEGATED_TO).getOrNull()

private val Attendee.dir: Dir?
    get() = getParameter<Dir>(Parameter.DIR).getOrNull()

private val Attendee.language: Language?
    get() = getParameter<Language>(Parameter.LANGUAGE).getOrNull()

private val Attendee.member: Member?
    get() = getParameter<Member>(Parameter.MEMBER).getOrNull()

private val Attendee.partStat: PartStat?
    get() = getParameter<PartStat>(Parameter.PARTSTAT).getOrNull()

private val Attendee.role: Role?
    get() = getParameter<Role>(Parameter.ROLE).getOrNull()

private val Attendee.rsvp: Rsvp?
    get() = getParameter<Rsvp>(Parameter.RSVP).getOrNull()

private val Attendee.sentBy: SentBy?
    get() = getParameter<SentBy>(Parameter.SENT_BY).getOrNull()
