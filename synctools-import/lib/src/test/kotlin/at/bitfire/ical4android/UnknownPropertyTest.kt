/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import androidx.test.filters.SmallTest
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Uid
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnknownPropertyTest {

    @Test
    @SmallTest
    fun testFromJsonString() {
        val prop = UnknownProperty.fromJsonString("[ \"UID\", \"PropValue\" ]")
        assertTrue(prop is Uid)
        assertEquals("UID", prop.name)
        assertEquals("PropValue", prop.value)
    }

    @Test
    @SmallTest
    fun testFromJsonStringWithParameters() {
        val prop = UnknownProperty.fromJsonString("[ \"ATTENDEE\", \"PropValue\", { \"x-param1\": \"value1\", \"x-param2\": \"value2\" } ]")
        assertTrue(prop is Attendee)
        assertEquals("ATTENDEE", prop.name)
        assertEquals("PropValue", prop.value)
        assertEquals(2, prop.parameterList.all.size)
        assertEquals("value1", prop.getRequiredParameter<Parameter>("x-param1").value)
        assertEquals("value2", prop.getRequiredParameter<Parameter>("x-param2").value)
    }

    @Test(expected = JSONException::class)
    @SmallTest
    fun testFromInvalidJsonString() {
        UnknownProperty.fromJsonString("This isn't JSON")
    }


    @Test
    @SmallTest
    fun testToJsonString() {
        val attendee = Attendee("mailto:test@test.at")
        assertEquals(
            "ATTENDEE:mailto:test@test.at",
            attendee.toString().trim()
        )

        attendee += Rsvp(true)
        attendee += XParameter("X-My-Param", "SomeValue")
        assertEquals(
            "ATTENDEE;RSVP=TRUE;X-My-Param=SomeValue:mailto:test@test.at",
            attendee.toString().trim()
        )
    }

}