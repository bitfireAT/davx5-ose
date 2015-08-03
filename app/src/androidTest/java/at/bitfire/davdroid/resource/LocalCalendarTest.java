/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource;

import android.Manifest;
import android.accounts.Account;
import android.annotation.TargetApi;
import android.content.ContentProviderClient;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.test.InstrumentationTestCase;
import android.util.Log;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;

import java.text.ParseException;
import java.util.Calendar;

import at.bitfire.davdroid.DateUtils;
import lombok.Cleanup;

public class LocalCalendarTest extends InstrumentationTestCase {

    private static final String
            TAG = "davdroid.test",
            accountType = "at.bitfire.davdroid.test",
            calendarName = "DAVdroid_Test";

    Context targetContext;

    ContentProviderClient providerClient;
    final Account testAccount = new Account(calendarName, accountType);

	Uri calendarURI;
    LocalCalendar testCalendar;


    // helpers

    private Uri syncAdapterURI(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType)
                .appendQueryParameter(Calendars.ACCOUNT_NAME, accountType)
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true").
                        build();
    }

    private long insertNewEvent() throws RemoteException {
        ContentValues values = new ContentValues();
        values.put(Events.CALENDAR_ID, testCalendar.getId());
        values.put(Events.TITLE, "Test Event");
        values.put(Events.ALL_DAY, 0);
        values.put(Events.DTSTART, Calendar.getInstance().getTimeInMillis());
        values.put(Events.DTEND, Calendar.getInstance().getTimeInMillis());
        values.put(Events.EVENT_TIMEZONE, "UTC");
        values.put(Events.DIRTY, 1);
        return ContentUris.parseId(providerClient.insert(syncAdapterURI(Events.CONTENT_URI), values));
    }

    private void deleteEvent(long id) throws RemoteException {
        providerClient.delete(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id)), null, null);
    }


    // initialization

    @Override
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    protected void setUp() throws LocalStorageException, RemoteException {
        targetContext = getInstrumentation().getTargetContext();
        targetContext.enforceCallingOrSelfPermission(Manifest.permission.WRITE_CALENDAR, "No privileges for managing calendars");

        providerClient = targetContext.getContentResolver().acquireContentProviderClient(CalendarContract.AUTHORITY);

        prepareTestCalendar();
    }

    private void prepareTestCalendar() throws LocalStorageException, RemoteException {
        @Cleanup Cursor cursor = providerClient.query(Calendars.CONTENT_URI, new String[] { Calendars._ID },
                Calendars.ACCOUNT_TYPE + "=? AND " + Calendars.ACCOUNT_NAME + "=?",
                new String[] { testAccount.type, testAccount.name }, null);
        if (cursor != null && cursor.moveToNext())
	        calendarURI = ContentUris.withAppendedId(Calendars.CONTENT_URI, cursor.getLong(0));
        else {
	        ServerInfo.ResourceInfo info = new ServerInfo.ResourceInfo(ServerInfo.ResourceInfo.Type.CALENDAR, false, null, "Test Calendar", null, null);
	        calendarURI = LocalCalendar.create(testAccount, targetContext.getContentResolver(), info);
        }

	    Log.i(TAG, "Prepared test calendar " + calendarURI);
        testCalendar = new LocalCalendar(testAccount, providerClient, ContentUris.parseId(calendarURI), null);
    }

    @Override
    protected void tearDown() throws RemoteException {
        deleteTestCalendar();
    }

    protected void deleteTestCalendar() throws RemoteException {
        Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, testCalendar.id);
        if  (providerClient.delete(uri,null,null)>0)
            Log.i(TAG,"Deleted test calendar "+uri);
        else
            Log.e(TAG,"Couldn't delete test calendar "+uri);
    }


    // tests

	public void testBuildEntry() throws LocalStorageException, ParseException {
		final String vcardName = "testBuildEntry";

		final TimeZone tzVienna = DateUtils.tzRegistry.getTimeZone("Europe/Vienna");
		assertNotNull(tzVienna);

		// build and write event to calendar provider
		Event event = new Event(vcardName, null);
		event.summary = "Sample event";
		event.description = "Sample event with date/time";
		event.location = "Sample location";
		event.dtStart = new DtStart("20150501T120000", tzVienna);
		event.dtEnd = new DtEnd("20150501T130000", tzVienna);
		assertFalse(event.isAllDay());

		// set an alarm one day, two hours, three minutes and four seconds before begin of event
		event.getAlarms().add(new VAlarm(new Dur(-1, -2, -3, -4)));

		testCalendar.add(event);
		testCalendar.commit();

		// read and parse event from calendar provider
		Event event2 = testCalendar.findByRemoteName(vcardName, true);
		assertNotNull("Couldn't build and insert event", event);
		// compare with original event
		try {
			assertEquals(event.getSummary(), event2.getSummary());
			assertEquals(event.getDescription(), event2.getDescription());
			assertEquals(event.getLocation(), event2.getLocation());
			assertEquals(event.getDtStart(), event2.getDtStart());
			assertFalse(event2.isAllDay());

			assertEquals(1, event2.getAlarms().size());
			VAlarm alarm = event2.getAlarms().get(0);
			assertEquals(event.getSummary(), alarm.getDescription().getValue());  // should be built from event name
			assertEquals(new Dur(0, 0, -(24*60 + 60*2 + 3), 0), alarm.getTrigger().getDuration());   // calendar provider stores trigger in minutes
		} finally {
			testCalendar.delete(event);
		}
	}

	public void testBuildAllDayEntry() throws LocalStorageException, ParseException {
		final String vcardName = "testBuildAllDayEntry";

		// build and write event to calendar provider
		Event event = new Event(vcardName, null);
		event.summary = "All-day event";
		event.description = "All-day event for testing";
		event.location = "Sample location testBuildAllDayEntry";
		event.dtStart = new DtStart(new Date("20150501"));
		event.dtEnd = new DtEnd(new Date("20150502"));
		assertTrue(event.isAllDay());
		testCalendar.add(event);
		testCalendar.commit();

		// read and parse event from calendar provider
		Event event2 = testCalendar.findByRemoteName(vcardName, true);
		assertNotNull("Couldn't build and insert event", event);
		// compare with original event
		try {
			assertEquals(event.getSummary(), event2.getSummary());
			assertEquals(event.getDescription(), event2.getDescription());
			assertEquals(event.getLocation(), event2.getLocation());
			assertEquals(event.getDtStart(), event2.getDtStart());
			assertTrue(event2.isAllDay());
		} finally {
			testCalendar.delete(event);
		}
	}

	public void testCTags() throws LocalStorageException {
		assertNull(testCalendar.getCTag());
		
		final String cTag = "just-modified"; 
		testCalendar.setCTag(cTag);
		
		assertEquals(cTag, testCalendar.getCTag());
	}
	
	public void testFindNew() throws LocalStorageException, RemoteException {
		// at the beginning, there are no dirty events 
		assertTrue(testCalendar.findNew().length == 0);
		assertTrue(testCalendar.findUpdated().length == 0);
		
		// insert a "new" event
		final long id = insertNewEvent();
		try {
			// there must be one "new" event now
			assertTrue(testCalendar.findNew().length == 1);
			assertTrue(testCalendar.findUpdated().length == 0);
					
			// nothing has changed, the record must still be "new"
			// see issue #233
			assertTrue(testCalendar.findNew().length == 1);
			assertTrue(testCalendar.findUpdated().length == 0);
		} finally {
			deleteEvent(id);
		}
	}

}
