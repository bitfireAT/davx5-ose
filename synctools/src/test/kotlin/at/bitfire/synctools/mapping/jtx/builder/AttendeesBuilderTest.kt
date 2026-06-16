/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.parameterListOf
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AttendeesBuilderTest {

    private val builder = AttendeesBuilder()

    @Test
    fun `No ATTENDEE`() {
        val journal = VJournal()
        val output = Entity(ContentValues())

        builder.build(from = journal, main = journal, to = output)

        assertTrue(output.entityValues.isEmpty)
        assertTrue(output.subValues.isEmpty())
    }

    @Test
    fun `single ATTENDEE with all known and unknown parameters`() {
        val task = VToDo(
            propertyListOf(
                Attendee(
                    parameterListOf(
                        Cn("Jane Attendee"),
                        DelegatedTo("mailto:delegated-to@domain.example"),
                        DelegatedFrom("mailto:delegated-from@domain.example"),
                        CuType.INDIVIDUAL,
                        Dir("ldap://ldap.domain.example/something"),
                        Language("en-US"),
                        Member("mailto:list@domain.example"),
                        PartStat.ACCEPTED,
                        Role.REQ_PARTICIPANT,
                        Rsvp.FALSE,
                        SentBy("mailto:sent-by@domain.example"),
                        XParameter("key1", "value"),
                        XParameter("key2", "value")
                    ),
                    "mailto:attendee@domain.example"
                )
            )
        )
        val main = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = main, to = output)

        assertTrue(output.entityValues.isEmpty)
        assertEquals(1, output.subValues.size)
        val subValue = output.subValues.first()
        assertEquals(JtxContract.JtxAttendee.CONTENT_URI, subValue.uri)
        assertContentValuesEqual(
            contentValuesOf(
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
                JtxContract.JtxAttendee.OTHER to """{"key1":"value","key2":"value"}"""
            ),
            subValue.values
        )
    }

    @Test
    fun `multiple ATTENDEE properties`() {
        val task = VToDo(
            propertyListOf(
                Attendee("mailto:one@domain.example"),
                Attendee("mailto:two@domain.example")
            )
        )
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertTrue(output.entityValues.isEmpty)
        assertEquals(2, output.subValues.size)
        val subValueOne = output.subValues.first { "one" in it.values.getAsString(JtxContract.JtxAttendee.CALADDRESS) }
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxAttendee.CALADDRESS to "mailto:one@domain.example",
                JtxContract.JtxAttendee.CN to null,
                JtxContract.JtxAttendee.DELEGATEDTO to null,
                JtxContract.JtxAttendee.DELEGATEDFROM to null,
                JtxContract.JtxAttendee.CUTYPE to null,
                JtxContract.JtxAttendee.DIR to null,
                JtxContract.JtxAttendee.LANGUAGE to null,
                JtxContract.JtxAttendee.MEMBER to null,
                JtxContract.JtxAttendee.PARTSTAT to null,
                JtxContract.JtxAttendee.ROLE to null,
                JtxContract.JtxAttendee.RSVP to null,
                JtxContract.JtxAttendee.SENTBY to null,
                JtxContract.JtxAttendee.OTHER to null
            ),
            subValueOne.values
        )
        val subValueTwo = output.subValues.first { "two" in it.values.getAsString(JtxContract.JtxAttendee.CALADDRESS) }
        assertContentValuesEqual(
            contentValuesOf(
                JtxContract.JtxAttendee.CALADDRESS to "mailto:two@domain.example",
                JtxContract.JtxAttendee.CN to null,
                JtxContract.JtxAttendee.DELEGATEDTO to null,
                JtxContract.JtxAttendee.DELEGATEDFROM to null,
                JtxContract.JtxAttendee.CUTYPE to null,
                JtxContract.JtxAttendee.DIR to null,
                JtxContract.JtxAttendee.LANGUAGE to null,
                JtxContract.JtxAttendee.MEMBER to null,
                JtxContract.JtxAttendee.PARTSTAT to null,
                JtxContract.JtxAttendee.ROLE to null,
                JtxContract.JtxAttendee.RSVP to null,
                JtxContract.JtxAttendee.SENTBY to null,
                JtxContract.JtxAttendee.OTHER to null
            ),
            subValueTwo.values
        )
    }
}
