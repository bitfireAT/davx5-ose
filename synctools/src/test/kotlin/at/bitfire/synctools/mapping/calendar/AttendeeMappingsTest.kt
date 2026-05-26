/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.provider.CalendarContract.Attendees
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.property.Attendee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class AttendeeMappingsTest {

    companion object {
        const val DEFAULT_ORGANIZER = "organizer@example.com"

        val CuTypeFancy = CuType("X-FANCY")
        val RoleFancy = Role("X-FANCY")
    }

    @Test
    fun testAndroidToICalendar_TypeRequired_RelationshipAttendee() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
        }) {
            assertNull(cuType)
            assertNull(role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeRequired_RelationshipOrganizer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER)
        }) {
            assertNull(cuType)
            assertNull(role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeRequired_RelationshipPerformer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_PERFORMER)
        }) {
            assertEquals(CuType.GROUP, cuType)
            assertNull(role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeRequired_RelationshipSpeaker() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_SPEAKER)
        }) {
            assertNull(cuType)
            assertEquals(Role.CHAIR, role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeRequired_RelationshipNone() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_NONE)
        }) {
            assertEquals(CuType.UNKNOWN, cuType)
            assertNull(role)
        }
    }


    @Test
    fun testAndroidToICalendar_TypeOptional_RelationshipAttendee() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
        }) {
            assertNull(cuType)
            assertEquals(Role.OPT_PARTICIPANT, role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeOptional_RelationshipOrganizer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER)
        }) {
            assertNull(cuType)
            assertEquals(Role.OPT_PARTICIPANT, role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeOptional_RelationshipPerformer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_PERFORMER)
        }) {
            assertEquals(CuType.GROUP, cuType)
            assertEquals(Role.OPT_PARTICIPANT, role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeOptional_RelationshipSpeaker() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_SPEAKER)
        }) {
            assertNull(cuType)
            assertEquals(Role.CHAIR, role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeOptional_RelationshipNone() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_NONE)
        }) {
            assertEquals(CuType.UNKNOWN, cuType)
            assertEquals(Role.OPT_PARTICIPANT, role)
        }
    }


    @Test
    fun testAndroidToICalendar_TypeNone_RelationshipAttendee() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
        }) {
            assertNull(cuType)
            assertNull(role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeNone_RelationshipOrganizer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER)
        }) {
            assertNull(cuType)
            assertNull(role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeNone_RelationshipPerformer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_PERFORMER)
        }) {
            assertEquals(CuType.GROUP, cuType)
            assertNull(role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeNone_RelationshipSpeaker() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_SPEAKER)
        }) {
            assertNull(cuType)
            assertEquals(Role.CHAIR, role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeNone_RelationshipNone() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_NONE)
        }) {
            assertEquals(CuType.UNKNOWN, cuType)
            assertNull(role)
        }
    }


    @Test
    fun testAndroidToICalendar_TypeResource_RelationshipAttendee() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
        }) {
            assertEquals(CuType.RESOURCE, cuType)
            assertNull(role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeResource_RelationshipOrganizer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER)
        }) {
            assertEquals(CuType.RESOURCE, cuType)
            assertNull(role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeResource_RelationshipPerformer() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_PERFORMER)
        }) {
            assertEquals(CuType.ROOM, cuType)
            assertNull(role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeResource_RelationshipSpeaker() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_SPEAKER)
        }) {
            assertEquals(CuType.RESOURCE, cuType)
            assertEquals(Role.CHAIR, role)
        }
    }

    @Test
    fun testAndroidToICalendar_TypeResource_RelationshipNone() {
        testAndroidToICalendar(ContentValues().apply {
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_NONE)
        }) {
            assertEquals(CuType.RESOURCE, cuType)
            assertNull(role)
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleNone() {
        testICalendarToAndroid(Attendee("mailto:attendee@example.com")) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_ATTENDEE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleChair() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add(Role.CHAIR)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_SPEAKER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleReqParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add(Role.REQ_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_ATTENDEE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleOptParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add(Role.OPT_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_OPTIONAL,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_ATTENDEE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleNonParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add(Role.NON_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_NONE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_ATTENDEE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeNone_RoleXValue() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add(RoleFancy)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_ATTENDEE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleNone() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add(CuType.INDIVIDUAL)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_ATTENDEE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleChair() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.INDIVIDUAL)
                .add(Role.CHAIR)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_SPEAKER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleReqParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.INDIVIDUAL)
                .add(Role.REQ_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_ATTENDEE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleOptParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.INDIVIDUAL)
                .add(Role.OPT_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_OPTIONAL,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_ATTENDEE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleNonParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.INDIVIDUAL)
                .add(Role.NON_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_NONE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_ATTENDEE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeIndividual_RoleXValue() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.INDIVIDUAL)
                .add(RoleFancy)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_ATTENDEE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleNone() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add(CuType.UNKNOWN)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_NONE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleChair() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.UNKNOWN)
                .add(Role.CHAIR)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_SPEAKER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleReqParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.UNKNOWN)
                .add(Role.REQ_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_NONE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleOptParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.UNKNOWN)
                .add<Attendee>(Role.OPT_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_OPTIONAL,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_NONE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleNonParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.UNKNOWN)
                .add(Role.NON_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_NONE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_NONE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeUnknown_RoleXValue() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.UNKNOWN)
                .add(RoleFancy)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_NONE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleNone() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add(CuType.GROUP)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_PERFORMER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleChair() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.GROUP)
                .add(Role.CHAIR)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_SPEAKER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleReqParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.GROUP)
                .add(Role.REQ_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_PERFORMER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleOptParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.GROUP)
                .add(Role.OPT_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_OPTIONAL,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_PERFORMER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleNonParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.GROUP)
                .add(Role.NON_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_NONE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_PERFORMER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeGroup_RoleXValue() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.GROUP)
                .add(RoleFancy)
        ) {
            assertEquals(
                Attendees.TYPE_REQUIRED,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_PERFORMER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleNone() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add(CuType.RESOURCE)
        ) {
            assertEquals(
                Attendees.TYPE_RESOURCE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_NONE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleChair() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.RESOURCE)
                .add(Role.CHAIR)
        ) {
            assertEquals(
                Attendees.TYPE_RESOURCE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_SPEAKER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleReqParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.RESOURCE)
                .add(Role.REQ_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_RESOURCE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_NONE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleOptParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.RESOURCE)
                .add(Role.OPT_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_RESOURCE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_NONE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleNonParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.RESOURCE)
                .add(Role.NON_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_RESOURCE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_NONE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeResource_RoleXValue() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.RESOURCE)
                .add(RoleFancy)
        ) {
            assertEquals(
                Attendees.TYPE_RESOURCE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_NONE,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleNone() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add(CuType.ROOM)
        ) {
            assertEquals(
                Attendees.TYPE_RESOURCE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_PERFORMER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleChair() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.ROOM)
                .add(Role.CHAIR)
        ) {
            assertEquals(
                Attendees.TYPE_RESOURCE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_PERFORMER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleReqParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.ROOM)
                .add(Role.REQ_PARTICIPANT)
        ) {
            assertEquals(
                Attendees.TYPE_RESOURCE,
                getAsInteger(Attendees.ATTENDEE_TYPE)
            )
            assertEquals(
                Attendees.RELATIONSHIP_PERFORMER,
                getAsInteger(Attendees.ATTENDEE_RELATIONSHIP)
            )
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleOptParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.ROOM)
                .add(Role.OPT_PARTICIPANT)
        ) {
            assertEquals(Attendees.TYPE_RESOURCE, getAsInteger(Attendees.ATTENDEE_TYPE))
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleNonParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.ROOM)
                .add(Role.NON_PARTICIPANT)
        ) {
            assertEquals(Attendees.TYPE_RESOURCE, getAsInteger(Attendees.ATTENDEE_TYPE))
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeRoom_RoleXValue() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuType.ROOM)
                .add(RoleFancy)
        ) {
            assertEquals(Attendees.TYPE_RESOURCE, getAsInteger(Attendees.ATTENDEE_TYPE))
            assertEquals(Attendees.RELATIONSHIP_PERFORMER, getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
        }
    }


    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleNone() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add(CuTypeFancy)
        ) {
            assertEquals(Attendees.TYPE_REQUIRED, getAsInteger(Attendees.ATTENDEE_TYPE))
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleChair() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuTypeFancy)
                .add(Role.CHAIR)
        ) {
            assertEquals(Attendees.TYPE_REQUIRED, getAsInteger(Attendees.ATTENDEE_TYPE))
            assertEquals(Attendees.RELATIONSHIP_SPEAKER, getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleReqParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuTypeFancy)
                .add<Attendee>(Role.REQ_PARTICIPANT)
        ) {
            assertEquals(Attendees.TYPE_REQUIRED, getAsInteger(Attendees.ATTENDEE_TYPE))
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleOptParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuTypeFancy)
                .add(Role.OPT_PARTICIPANT)
        ) {
            assertEquals(Attendees.TYPE_OPTIONAL, getAsInteger(Attendees.ATTENDEE_TYPE))
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleNonParticipant() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuTypeFancy)
                .add(Role.NON_PARTICIPANT)
        ) {
            assertEquals(Attendees.TYPE_NONE, getAsInteger(Attendees.ATTENDEE_TYPE))
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
        }
    }

    @Test
    fun testICalendarToAndroid_CuTypeXValue_RoleXValue() {
        testICalendarToAndroid(
            Attendee("mailto:attendee@example.com")
                .add<Attendee>(CuTypeFancy)
                .add(RoleFancy)
        ) {
            assertEquals(Attendees.TYPE_REQUIRED, getAsInteger(Attendees.ATTENDEE_TYPE))
            assertEquals(Attendees.RELATIONSHIP_ATTENDEE, getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
        }
    }


    @Test
    fun testICalendarToAndroid_Organizer() {
        testICalendarToAndroid(Attendee("mailto:$DEFAULT_ORGANIZER")) {
            assertEquals(Attendees.RELATIONSHIP_ORGANIZER, getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
        }
    }



    // helpers

    private fun testICalendarToAndroid(attendee: Attendee, organizer: String = DEFAULT_ORGANIZER, test: (ContentValues).() -> Unit) {
        val values = ContentValues()
        AttendeeMappings.iCalendarToAndroid(attendee, values, organizer)
        test(values)
    }

    private fun testAndroidToICalendar(values: ContentValues, test: (Attendee).() -> Unit) {
        val attendee = Attendee()
        AttendeeMappings.androidToICalendar(values, attendee)
        test(attendee)
    }

}

private val Attendee.cuType: CuType?
    get() = getParameter<CuType>(Parameter.CUTYPE).getOrNull()

private val Attendee.role: Role?
    get() = getParameter<Role>(Parameter.ROLE).getOrNull()
