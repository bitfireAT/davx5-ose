/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model

import android.content.ContentValues
import androidx.test.filters.SmallTest
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.property.ResourceType
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.model.ServiceDB.Collections
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CollectionInfoTest {

    private lateinit var httpClient: HttpClient
    private val server = MockWebServer()

    @Before
    fun setUp() {
        httpClient = HttpClient.Builder().build()
    }

    @After
    fun shutDown() {
        httpClient.close()
    }


    @Test
    @SmallTest
    fun testFromDavResource() {
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

        var info: CollectionInfo? = null
        DavResource(httpClient.okHttpClient, server.url("/"))
                .propfind(0, ResourceType.NAME) { response, _ ->
            info = CollectionInfo(response)
        }
        assertEquals(CollectionInfo.Type.ADDRESS_BOOK, info?.type)
        assertTrue(info!!.privWriteContent)
        assertTrue(info!!.privUnbind)
        assertEquals("My Contacts", info?.displayName)
        assertEquals("My Contacts Description", info?.description)

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
                        "       <CAL:calendar-timezone>tzdata</CAL:calendar-timezone>" +
                        "       <ICAL:calendar-color>#ff0000</ICAL:calendar-color>" +
                        "   </prop></propstat>" +
                        "</response>" +
                        "</multistatus>"))

        info = null
        DavResource(httpClient.okHttpClient, server.url("/"))
                .propfind(0, ResourceType.NAME) { response, _ ->
            info = CollectionInfo(response)
        }
        assertEquals(CollectionInfo.Type.CALENDAR, info?.type)
        assertFalse(info!!.privWriteContent)
        assertFalse(info!!.privUnbind)
        assertNull(info?.displayName)
        assertEquals("My Calendar", info?.description)
        assertEquals(0xFFFF0000.toInt(), info?.color)
        assertEquals("tzdata", info?.timeZone)
        assertTrue(info!!.supportsVEVENT)
        assertTrue(info!!.supportsVTODO)
    }

    @Test
    fun testFromDB() {
        val values = ContentValues()
        values.put(Collections.ID, 1)
        values.put(Collections.SERVICE_ID, 1)
        values.put(Collections.TYPE, CollectionInfo.Type.CALENDAR.name)
        values.put(Collections.URL, "http://example.com")
        values.put(Collections.PRIV_WRITE_CONTENT, 0)
        values.put(Collections.PRIV_UNBIND, 0)
        values.put(Collections.DISPLAY_NAME, "display name")
        values.put(Collections.DESCRIPTION, "description")
        values.put(Collections.COLOR, 0xFFFF0000)
        values.put(Collections.TIME_ZONE, "tzdata")
        values.put(Collections.SUPPORTS_VEVENT, 1)
        values.put(Collections.SUPPORTS_VTODO, 1)
        values.put(Collections.SYNC, 1)

        val info = CollectionInfo(values)
        assertEquals(CollectionInfo.Type.CALENDAR, info.type)
        assertEquals(1.toLong(), info.id)
        assertEquals(1.toLong(), info.serviceID)
        assertEquals(HttpUrl.parse("http://example.com/"), info.url)
        assertFalse(info.privWriteContent)
        assertFalse(info.privUnbind)
        assertEquals("display name", info.displayName)
        assertEquals("description", info.description)
        assertEquals(0xFFFF0000.toInt(), info.color)
        assertEquals("tzdata", info.timeZone)
        assertTrue(info.supportsVEVENT)
        assertTrue(info.supportsVTODO)
        assertTrue(info.selected)
    }

}
