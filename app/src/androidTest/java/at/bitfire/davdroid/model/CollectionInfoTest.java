/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model;

import android.content.ContentValues;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.ResourceType;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CollectionInfoTest {

    HttpClient httpClient;
    MockWebServer server = new MockWebServer();

    @Before
    public void setUp() {
        httpClient = new HttpClient.Builder().build();
    }

    @After
    public void shutDown() {
        httpClient.close();
    }


    @Test
    public void testFromDavResource() throws IOException, HttpException, DavException {
        // r/w address book
        server.enqueue(new MockResponse()
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
                        "</multistatus>"));

        DavResource dav = new DavResource(httpClient.getOkHttpClient(), server.url("/"));
        dav.propfind(0, ResourceType.NAME);
        CollectionInfo info = new CollectionInfo(dav);
        assertEquals(CollectionInfo.Type.ADDRESS_BOOK, info.getType());
        assertFalse(info.getReadOnly());
        assertEquals("My Contacts", info.getDisplayName());
        assertEquals("My Contacts Description", info.getDescription());

        // read-only calendar, no display name
        server.enqueue(new MockResponse()
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
                        "</multistatus>"));

        dav = new DavResource(httpClient.getOkHttpClient(), server.url("/"));
        dav.propfind(0, ResourceType.NAME);
        info = new CollectionInfo(dav);
        assertEquals(CollectionInfo.Type.CALENDAR, info.getType());
        assertTrue(info.getReadOnly());
        assertNull(info.getDisplayName());
        assertEquals("My Calendar", info.getDescription());
        assertEquals(0xFFFF0000, (int)info.getColor());
        assertEquals("tzdata", info.getTimeZone());
        assertTrue(info.getSupportsVEVENT());
        assertTrue(info.getSupportsVTODO());
    }

    @Test
    public void testFromDB() {
        ContentValues values = new ContentValues();
        values.put(Collections.ID, 1);
        values.put(Collections.SERVICE_ID, 1);
        values.put(Collections.TYPE, CollectionInfo.Type.CALENDAR.name());
        values.put(Collections.URL, "http://example.com");
        values.put(Collections.READ_ONLY, 1);
        values.put(Collections.DISPLAY_NAME, "display name");
        values.put(Collections.DESCRIPTION, "description");
        values.put(Collections.COLOR, 0xFFFF0000);
        values.put(Collections.TIME_ZONE, "tzdata");
        values.put(Collections.SUPPORTS_VEVENT, 1);
        values.put(Collections.SUPPORTS_VTODO, 1);
        values.put(Collections.SYNC, 1);

        CollectionInfo info = new CollectionInfo(values);
        assertEquals(CollectionInfo.Type.CALENDAR, info.getType());
        assertEquals(1, (long)info.getId());
        assertEquals(1, (long)info.getServiceID());
        assertEquals("http://example.com", info.getUrl());
        assertTrue(info.getReadOnly());
        assertEquals("display name", info.getDisplayName());
        assertEquals("description", info.getDescription());
        assertEquals(0xFFFF0000, (int)info.getColor());
        assertEquals("tzdata", info.getTimeZone());
        assertTrue(info.getSupportsVEVENT());
        assertTrue(info.getSupportsVTODO());
        assertTrue(info.getSelected());
    }

}
