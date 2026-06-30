/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.util.DavUtils.toUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionTest {

    private val xmlHeaders = headersOf(HttpHeaders.ContentType, "application/xml; charset=UTF-8")
    private val baseUrl = Url("https://dav.example.com/")

    private fun mockClient(xmlBody: String): HttpClient =
        HttpClient(MockEngine { respond(xmlBody, HttpStatusCode.MultiStatus, xmlHeaders) })


    @Test
    fun testFromDavResponseAddressBook() = runTest {
        mockClient(
            "<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav'>" +
                    "<response>" +
                    "   <href>/</href>" +
                    "   <propstat><prop>" +
                    "       <resourcetype><collection/><CARD:addressbook/></resourcetype>" +
                    "       <displayname>My Contacts</displayname>" +
                    "       <CARD:addressbook-description>My Contacts Description</CARD:addressbook-description>" +
                    "   </prop></propstat>" +
                    "</response>" +
                    "</multistatus>"
        ).use { client ->
            val davResource = DavResource(client, baseUrl)
            var collectionFromResponse: Collection? = null
            davResource.propfind(0, WebDAV.ResourceType) { response, _ ->
                collectionFromResponse = Collection.fromDavResponse(response)
            }
            assertNotNull(collectionFromResponse)
            val collection = collectionFromResponse!!
            assertEquals(Collection.TYPE_ADDRESSBOOK, collection.type)
            assertTrue(collection.privWriteContent)
            assertTrue(collection.privUnbind)
            assertNull(collection.supportsVEVENT)
            assertNull(collection.supportsVTODO)
            assertNull(collection.supportsVJOURNAL)
            assertEquals("My Contacts", collection.displayName)
            assertEquals("My Contacts Description", collection.description)
        }
    }

    @Test
    fun testFromDavResponseCalendar_FullTimezone() = runTest {
        mockClient(
            "<multistatus xmlns='DAV:' xmlns:CAL='urn:ietf:params:xml:ns:caldav' xmlns:ICAL='http://apple.com/ns/ical/'>" +
                    "<response>" +
                    "   <href>/</href>" +
                    "   <propstat><prop>" +
                    "       <resourcetype><collection/><CAL:calendar/></resourcetype>" +
                    "       <current-user-privilege-set><privilege><read/></privilege></current-user-privilege-set>" +
                    "       <CAL:calendar-description>My Calendar</CAL:calendar-description>" +
                    "       <CAL:calendar-timezone>BEGIN:VCALENDAR\n" +
                    "PRODID:-//Example Corp.//CalDAV Client//EN\n" +
                    "VERSION:2.0\n" +
                    "BEGIN:VTIMEZONE\n" +
                    "TZID:US-Eastern\n" +
                    "LAST-MODIFIED:19870101T000000Z\n" +
                    "BEGIN:STANDARD\n" +
                    "DTSTART:19671029T020000\n" +
                    "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
                    "TZOFFSETFROM:-0400\n" +
                    "TZOFFSETTO:-0500\n" +
                    "TZNAME:Eastern Standard Time (US & Canada)\n" +
                    "END:STANDARD\n" +
                    "BEGIN:DAYLIGHT\n" +
                    "DTSTART:19870405T020000\n" +
                    "RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=4\n" +
                    "TZOFFSETFROM:-0500\n" +
                    "TZOFFSETTO:-0400\n" +
                    "TZNAME:Eastern Daylight Time (US & Canada)\n" +
                    "END:DAYLIGHT\n" +
                    "END:VTIMEZONE\n" +
                    "END:VCALENDAR\n" +
                    "</CAL:calendar-timezone>" +
                    "       <ICAL:calendar-color>#ff0000</ICAL:calendar-color>" +
                    "   </prop></propstat>" +
                    "</response>" +
                    "</multistatus>"
        ).use { client ->
            val davResource = DavResource(client, baseUrl)
            var collectionFromResponse: Collection? = null
            davResource.propfind(0, WebDAV.ResourceType) { response, _ ->
                collectionFromResponse = Collection.fromDavResponse(response)
            }
            assertNotNull(collectionFromResponse)
            val collection = collectionFromResponse!!
            assertEquals(Collection.TYPE_CALENDAR, collection.type)
            assertFalse(collection.privWriteContent)
            assertFalse(collection.privUnbind)
            assertNull(collection.displayName)
            assertEquals("My Calendar", collection.description)
            assertEquals(0xFFFF0000.toInt(), collection.color)
            assertEquals("US-Eastern", collection.timezoneId)
            assertTrue(collection.supportsVEVENT!!)
            assertTrue(collection.supportsVTODO!!)
            assertTrue(collection.supportsVJOURNAL!!)
        }
    }

    @Test
    fun testFromDavResponseCalendar_OnlyTzId() = runTest {
        mockClient(
            "<multistatus xmlns='DAV:' xmlns:CAL='urn:ietf:params:xml:ns:caldav' xmlns:ICAL='http://apple.com/ns/ical/'>" +
                    "<response>" +
                    "   <href>/</href>" +
                    "   <propstat><prop>" +
                    "       <resourcetype><collection/><CAL:calendar/></resourcetype>" +
                    "       <current-user-privilege-set><privilege><read/></privilege></current-user-privilege-set>" +
                    "       <CAL:calendar-description>My Calendar</CAL:calendar-description>" +
                    "       <CAL:calendar-timezone-id>US-Eastern</CAL:calendar-timezone-id>" +
                    "       <ICAL:calendar-color>#ff0000</ICAL:calendar-color>" +
                    "   </prop></propstat>" +
                    "</response>" +
                    "</multistatus>"
        ).use { client ->
            val davResource = DavResource(client, baseUrl)
            var collectionFromResponse: Collection? = null
            davResource.propfind(0, WebDAV.ResourceType) { response, _ ->
                collectionFromResponse = Collection.fromDavResponse(response)
            }
            assertNotNull(collectionFromResponse)
            val collection = collectionFromResponse!!
            assertEquals(Collection.TYPE_CALENDAR, collection.type)
            assertFalse(collection.privWriteContent)
            assertFalse(collection.privUnbind)
            assertNull(collection.displayName)
            assertEquals("My Calendar", collection.description)
            assertEquals(0xFFFF0000.toInt(), collection.color)
            assertEquals("US-Eastern", collection.timezoneId)
            assertTrue(collection.supportsVEVENT!!)
            assertTrue(collection.supportsVTODO!!)
            assertTrue(collection.supportsVJOURNAL!!)
        }
    }

    @Test
    fun testFromDavResponseWebcal() = runTest {
        mockClient(
            "<multistatus xmlns='DAV:' xmlns:CS='http://calendarserver.org/ns/'>" +
                    "<response>" +
                    "   <href>/webcal1</href>" +
                    "   <propstat><prop>" +
                    "       <displayname>Sample Subscription</displayname>" +
                    "       <resourcetype><collection/><CS:subscribed/></resourcetype>" +
                    "       <CS:source><href>webcals://example.com/1.ics</href></CS:source>" +
                    "   </prop></propstat>" +
                    "</response>" +
                    "</multistatus>"
        ).use { client ->
            val davResource = DavResource(client, baseUrl)
            var collectionFromResponse: Collection? = null
            davResource.propfind(0, WebDAV.ResourceType) { response, _ ->
                collectionFromResponse = Collection.fromDavResponse(response)
            }
            assertNotNull(collectionFromResponse)
            val collection = collectionFromResponse!!
            assertEquals(Collection.TYPE_WEBCAL, collection.type)
            assertEquals("Sample Subscription", collection.displayName)
            assertEquals("https://example.com/1.ics".toUrl(), collection.source)
        }
    }

}
