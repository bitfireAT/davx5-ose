/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.property.Attendee
import org.junit.Assert.assertEquals
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
    fun `Attendee is email address`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com"
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertEquals(
            URI("mailto:attendee@example.com"),
            result.firstAttendee.calAddress
        )
    }

    @Test
    fun `Attendee is other URI`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_ID_NAMESPACE to "https",
            Attendees.ATTENDEE_IDENTITY to "//example.com/principals/attendee"
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertEquals(
            URI("https://example.com/principals/attendee"),
            result.firstAttendee.calAddress
        )
    }

    @Test
    fun `Attendee is email address with other URI`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_ID_NAMESPACE to "https",
            Attendees.ATTENDEE_IDENTITY to "//example.com/principals/attendee"
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        val attendees = result.getProperties<Attendee>(Property.ATTENDEE)
        assertEquals(1, attendees.size)
        val attendee = attendees.first()
        assertEquals(URI("https://example.com/principals/attendee"), attendee.calAddress)
        assertEquals("attendee@example.com", attendee.getParameter<Email>(Parameter.EMAIL).get().value)
    }


    @Test
    fun `Attendee with relationship ATTENDEE or ORGANIZER generates empty user-type`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER))
            for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null)) {
                val entity = Entity(ContentValues())
                entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                    Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                    Attendees.ATTENDEE_RELATIONSHIP to relationship,
                    Attendees.ATTENDEE_TYPE to type
                ))
                val result = VEvent()
                handler.process(entity, entity, result)
                assertNull(result.firstAttendee.cuType)
            }
    }

    @Test
    fun `Attendee with relationship PERFORMER generates user-type GROUP`() {
        for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_PERFORMER,
                Attendees.ATTENDEE_TYPE to type
            ))
            val result = VEvent()
            handler.process(entity, entity, result)
            assertEquals(CuType.GROUP, result.firstAttendee.cuType)
        }
    }

    @Test
    fun `Attendee with relationship SPEAKER generates chair role (user-type person)`() {
        for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_SPEAKER,
                Attendees.ATTENDEE_TYPE to type
            ))
            val result = VEvent()
            handler.process(entity, entity, result)
            val attendee = result.firstAttendee
            assertNull(attendee.cuType)
            assertEquals(Role.CHAIR, attendee.role)
        }
    }

    @Test
    fun `Attendee with relationship SPEAKER generates chair role (user-type RESOURCE)`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_SPEAKER,
            Attendees.ATTENDEE_TYPE to Attendees.TYPE_RESOURCE
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        val attendee = result.firstAttendee
        assertEquals(CuType.RESOURCE, attendee.cuType)
        assertEquals(Role.CHAIR, attendee.role)
    }

    @Test
    fun `Attendee with relationship NONE generates user-type UNKNOWN`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_NONE, null))
            for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null)) {
                val entity = Entity(ContentValues())
                entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                    Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                    Attendees.ATTENDEE_RELATIONSHIP to relationship,
                    Attendees.ATTENDEE_TYPE to type
                ))
                val result = VEvent()
                handler.process(entity, entity, result)
                assertEquals(CuType.UNKNOWN, result.firstAttendee.cuType)
            }
    }


    @Test
    fun `Attendee with type NONE doesn't generate ROLE`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_PERFORMER, Attendees.RELATIONSHIP_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to relationship,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_NONE
            ))
            val result = VEvent()
            handler.process(entity, entity, result)
            assertNull(result.firstAttendee.role)
        }
    }

    @Test
    fun `Attendee with type REQUIRED doesn't generate ROLE`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_PERFORMER, Attendees.RELATIONSHIP_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to relationship,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_REQUIRED
            ))
            val result = VEvent()
            handler.process(entity, entity, result)
            assertNull(result.firstAttendee.role)
        }
    }

    @Test
    fun `Attendee with type OPTIONAL generates OPTIONAL role`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_PERFORMER, Attendees.RELATIONSHIP_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to relationship,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_OPTIONAL
            ))
            val result = VEvent()
            handler.process(entity, entity, result)
            assertEquals(Role.OPT_PARTICIPANT, result.firstAttendee.role)
        }
    }

    @Test
    fun `Attendee with type RESOURCE generates user-type RESOURCE`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to relationship,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_RESOURCE
            ))
            val result = VEvent()
            handler.process(entity, entity, result)
            assertEquals(CuType.RESOURCE, result.firstAttendee.cuType)
        }
    }

    @Test
    fun `Attendee with type RESOURCE (relationship PERFORMER) generates user-type ROOM`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_PERFORMER,
            Attendees.ATTENDEE_TYPE to Attendees.TYPE_RESOURCE
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertEquals(CuType.ROOM, result.firstAttendee.cuType)
    }


    @Test
    fun `Attendee without participation status`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com"
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertNull(result.firstAttendee.partStat)
    }

    @Test
    fun `Attendee with participation status INVITED`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_INVITED
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertEquals(PartStat.NEEDS_ACTION, result.firstAttendee.partStat)
    }

    @Test
    fun `Attendee with participation status ACCEPTED`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_ACCEPTED
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertEquals(PartStat.ACCEPTED, result.firstAttendee.partStat)
    }

    @Test
    fun `Attendee with participation status DECLINED`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_DECLINED
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertEquals(PartStat.DECLINED, result.firstAttendee.partStat)
    }

    @Test
    fun `Attendee with participation status TENTATIVE`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_TENTATIVE
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertEquals(PartStat.TENTATIVE, result.firstAttendee.partStat)
    }

    @Test
    fun `Attendee with participation status NONE`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_NONE
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertNull(result.firstAttendee.partStat)
    }


    @Test
    fun `Attendee RSVP`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com"
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        assertTrue(result.firstAttendee.getParameter<Rsvp>(Parameter.RSVP).get().rsvp)
    }

}

private val VEvent.firstAttendee: Attendee
    get() = getProperty<Attendee>(Property.ATTENDEE).get()

private val Attendee.cuType: CuType?
    get() = getParameter<CuType>(Parameter.CUTYPE).getOrNull()

private val Attendee.role: Role?
    get() = getParameter<Role>(Parameter.ROLE).getOrNull()

private val Attendee.partStat: PartStat?
    get() = getParameter<PartStat>(Parameter.PARTSTAT).getOrNull()
