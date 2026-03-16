/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.test.filters.SmallTest
import at.bitfire.dav4jvm.okhttp.DavResource
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.network.HttpClientBuilder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class CollectionTest {

    @Inject
    lateinit var httpClientBuilder: HttpClientBuilder

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var httpClient: OkHttpClient
    private val server = MockWebServer()

    @Before
    fun setup() {
        hiltRule.inject()

        httpClient = httpClientBuilder.build()
    }


    @Test
    @SmallTest
    fun testFromDavResponseAddressBook() {
        // r/w address book
        server.enqueue(MockResponse()
                .setResponseCode(207)
                .setBody("<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav'>" +
                        "<response>" +
                        "   <href>/</href>" +
                        "   <propstat><prop>" +
                        "       <resourcetype><collection/><CARD:addressbook/></resourcetype>" +
                        "       <displayname>My Contacts</displayname>" +
                        "       <CARD:addressbook-description>My Contacts Description</CARD:addressbook-description>" +
                        "   </prop></propstat>" +
                        "</response>" +
                        "</multistatus>"))

        lateinit var info: Collection
        DavResource(httpClient, server.url("/"))
                .propfind(0, WebDAV.ResourceType) { response, _ ->
            info = Collection.fromDavResponse(response) ?: throw IllegalArgumentException()
        }
        assertEquals(Collection.TYPE_ADDRESSBOOK, info.type)
        assertTrue(info.privWriteContent)
        assertTrue(info.privUnbind)
        assertNull(info.supportsVEVENT)
        assertNull(info.supportsVTODO)
        assertNull(info.supportsVJOURNAL)
        assertEquals("My Contacts", info.displayName)
        assertEquals("My Contacts Description", info.description)
    }

    @Test
    @SmallTest
    fun testFromDavResponseCalendar_FullTimezone() {
        // read-only calendar, no display name
        server.enqueue(MockResponse()
            .setResponseCode(207)
            .setBody("<multistatus xmlns='DAV:' xmlns:CAL='urn:ietf:params:xml:ns:caldav' xmlns:ICAL='http://apple.com/ns/ical/'>" +
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
                    "</multistatus>"))

        lateinit var info: Collection
        DavResource(httpClient, server.url("/"))
            .propfind(0, WebDAV.ResourceType) { response, _ ->
                info = Collection.fromDavResponse(response)!!
            }
        assertEquals(Collection.TYPE_CALENDAR, info.type)
        assertFalse(info.privWriteContent)
        assertFalse(info.privUnbind)
        assertNull(info.displayName)
        assertEquals("My Calendar", info.description)
        assertEquals(0xFFFF0000.toInt(), info.color)
        assertEquals("US-Eastern", info.timezoneId)
        assertTrue(info.supportsVEVENT!!)
        assertTrue(info.supportsVTODO!!)
        assertTrue(info.supportsVJOURNAL!!)
    }

    @Test
    @SmallTest
    fun testFromDavResponseCalendar_OnlyTzId() {
        // read-only calendar, no display name
        server.enqueue(MockResponse()
            .setResponseCode(207)
            .setBody("<multistatus xmlns='DAV:' xmlns:CAL='urn:ietf:params:xml:ns:caldav' xmlns:ICAL='http://apple.com/ns/ical/'>" +
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
                    "</multistatus>"))

        lateinit var info: Collection
        DavResource(httpClient, server.url("/"))
            .propfind(0, WebDAV.ResourceType) { response, _ ->
                info = Collection.fromDavResponse(response)!!
            }
        assertEquals(Collection.TYPE_CALENDAR, info.type)
        assertFalse(info.privWriteContent)
        assertFalse(info.privUnbind)
        assertNull(info.displayName)
        assertEquals("My Calendar", info.description)
        assertEquals(0xFFFF0000.toInt(), info.color)
        assertEquals("US-Eastern", info.timezoneId)
        assertTrue(info.supportsVEVENT!!)
        assertTrue(info.supportsVTODO!!)
        assertTrue(info.supportsVJOURNAL!!)
    }

    @Test
    @SmallTest
    fun testFromDavResponseWebcal() {
        // Webcal subscription
        server.enqueue(MockResponse()
                .setResponseCode(207)
                .setBody("<multistatus xmlns='DAV:' xmlns:CS='http://calendarserver.org/ns/'>" +
                        "<response>" +
                        "   <href>/webcal1</href>" +
                        "   <propstat><prop>" +
                        "       <displayname>Sample Subscription</displayname>" +
                        "       <resourcetype><collection/><CS:subscribed/></resourcetype>" +
                        "       <CS:source><href>webcals://example.com/1.ics</href></CS:source>" +
                        "   </prop></propstat>" +
                        "</response>" +
                        "</multistatus>"))

        lateinit var info: Collection
        DavResource(httpClient, server.url("/"))
                .propfind(0, WebDAV.ResourceType) { response, _ ->
                    info = Collection.fromDavResponse(response) ?: throw IllegalArgumentException()
                }
        assertEquals(Collection.TYPE_WEBCAL, info.type)
        assertEquals("Sample Subscription", info.displayName)
        assertEquals("https://example.com/1.ics".toHttpUrl(), info.source)
    }

}