/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model;

import android.content.ContentValues;

import junit.framework.TestCase;

import java.io.IOException;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.ResourceType;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class CollectionInfoTest extends TestCase {

    MockWebServer server = new MockWebServer();

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

        DavResource dav = new DavResource(HttpClient.create(), server.url("/"));
        dav.propfind(0, ResourceType.NAME);
        CollectionInfo info = CollectionInfo.fromDavResource(dav);
        assertEquals(CollectionInfo.Type.ADDRESS_BOOK, info.type);
        assertFalse(info.readOnly);
        assertEquals("My Contacts", info.displayName);
        assertEquals("My Contacts Description", info.description);

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

        dav = new DavResource(HttpClient.create(), server.url("/"));
        dav.propfind(0, ResourceType.NAME);
        info = CollectionInfo.fromDavResource(dav);
        assertEquals(CollectionInfo.Type.CALENDAR, info.type);
        assertTrue(info.readOnly);
        assertNull(info.displayName);
        assertEquals("My Calendar", info.description);
        assertEquals(0xFFFF0000, (int)info.color);
        assertEquals("tzdata", info.timeZone);
        assertTrue(info.supportsVEVENT);
        assertTrue(info.supportsVTODO);
    }

    public void testFromDB() {
        ContentValues values = new ContentValues();
        values.put(Collections.ID, 1);
        values.put(Collections.SERVICE_ID, 1);
        values.put(Collections.URL, "http://example.com");
        values.put(Collections.READ_ONLY, 1);
        values.put(Collections.DISPLAY_NAME, "display name");
        values.put(Collections.DESCRIPTION, "description");
        values.put(Collections.COLOR, 0xFFFF0000);
        values.put(Collections.TIME_ZONE, "tzdata");
        values.put(Collections.SUPPORTS_VEVENT, 1);
        values.put(Collections.SUPPORTS_VTODO, 1);
        values.put(Collections.SYNC, 1);

        CollectionInfo info = CollectionInfo.fromDB(values);
        assertEquals(1, info.id);
        assertEquals(1, (long)info.serviceID);
        assertEquals("http://example.com", info.url);
        assertTrue(info.readOnly);
        assertEquals("display name", info.displayName);
        assertEquals("description", info.description);
        assertEquals(0xFFFF0000, (int)info.color);
        assertEquals("tzdata", info.timeZone);
        assertTrue(info.supportsVEVENT);
        assertTrue(info.supportsVTODO);
        assertTrue(info.selected);
    }

}
