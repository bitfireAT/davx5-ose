/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.resource.test;

import java.util.Calendar;

import lombok.Cleanup;
import android.accounts.Account;
import android.annotation.TargetApi;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.test.InstrumentationTestCase;
import android.util.Log;
import at.bitfire.davdroid.resource.LocalCalendar;
import at.bitfire.davdroid.resource.LocalStorageException;

public class LocalCalendarTest extends InstrumentationTestCase {
	
	private static final String
		TAG = "davroid.LocalCalendarTest",
		calendarName = "DAVdroid_Test";
	
	ContentProviderClient providerClient;
	Account testAccount = new Account(calendarName, CalendarContract.ACCOUNT_TYPE_LOCAL);
	LocalCalendar testCalendar;
	
	
	// helpers
	
	private Uri syncAdapterURI(Uri uri) {
		return uri.buildUpon()
				.appendQueryParameter(Calendars.ACCOUNT_NAME, calendarName)
				.appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
				.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true").
				build();
	}
	
	private long insertNewEvent() throws LocalStorageException, RemoteException {
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
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
	protected void setUp() throws Exception {
		ContentResolver resolver = getInstrumentation().getContext().getContentResolver();
		providerClient = resolver.acquireContentProviderClient(CalendarContract.AUTHORITY);
		
		long id;
		
		@Cleanup Cursor cursor = providerClient.query(Calendars.CONTENT_URI,
				new String[] { Calendars._ID },
				Calendars.ACCOUNT_TYPE + "=? AND " + Calendars.NAME + "=?",
				new String[] { CalendarContract.ACCOUNT_TYPE_LOCAL, calendarName },
				null);
		if (cursor.moveToNext()) {
			// found local test calendar
			id = cursor.getLong(0);
			Log.d(TAG, "Found test calendar with ID " + id);
			
		} else {
			// no local test calendar found, create 
			ContentValues values = new ContentValues();
			values.put(Calendars.ACCOUNT_NAME, testAccount.name);
			values.put(Calendars.ACCOUNT_TYPE, testAccount.type);
			values.put(Calendars.NAME, calendarName);
			values.put(Calendars.CALENDAR_DISPLAY_NAME, calendarName);
			values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
			values.put(Calendars.ALLOWED_REMINDERS, Reminders.METHOD_ALERT);
			values.put(Calendars.SYNC_EVENTS, 0);
			values.put(Calendars.VISIBLE, 1);
			
			if (android.os.Build.VERSION.SDK_INT >= 15) {
				values.put(Calendars.ALLOWED_AVAILABILITY, Events.AVAILABILITY_BUSY + "," + Events.AVAILABILITY_FREE + "," + Events.AVAILABILITY_TENTATIVE);
				values.put(Calendars.ALLOWED_ATTENDEE_TYPES, Attendees.TYPE_NONE + "," + Attendees.TYPE_OPTIONAL + "," + Attendees.TYPE_REQUIRED + "," + Attendees.TYPE_RESOURCE);
			}
			
			Uri calendarURI = providerClient.insert(syncAdapterURI(Calendars.CONTENT_URI), values);
			
			id = ContentUris.parseId(calendarURI);
			Log.d(TAG, "Created test calendar with ID " + id);
		}
		
		testCalendar = new LocalCalendar(testAccount, providerClient, id, null);
	}

	protected void tearDown() throws Exception {
		Uri uri = ContentUris.withAppendedId(syncAdapterURI(Calendars.CONTENT_URI), testCalendar.getId());
		providerClient.delete(uri, null, null);
	}

	
	// tests
	
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
		long id = insertNewEvent();
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
