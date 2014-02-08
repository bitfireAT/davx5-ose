package at.bitfire.davdroid.resource.test;

import lombok.Cleanup;
import android.annotation.TargetApi;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.test.InstrumentationTestCase;

public class LocalCalendarTest extends InstrumentationTestCase {
	
	private static final String calendarName = "DavdroidTest";
	
	ContentProviderClient client;
	long calendarID;
	

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
	protected void setUp() throws Exception {
		// get content resolver
		ContentResolver resolver = getInstrumentation().getContext().getContentResolver();
		client = resolver.acquireContentProviderClient(CalendarContract.AUTHORITY);
		
		@Cleanup Cursor cursor = client.query(Calendars.CONTENT_URI,
				new String[] { Calendars._ID },
				Calendars.ACCOUNT_TYPE + "=? AND " + Calendars.NAME + "=?",
				new String[] { CalendarContract.ACCOUNT_TYPE_LOCAL, calendarName },
				null);
		if (cursor.moveToNext()) {
			// found local test calendar
			calendarID = cursor.getLong(0);
		} else {
			// no local test calendar found, create 
			ContentValues values = new ContentValues();
			values.put(Calendars.ACCOUNT_NAME, calendarName);
			values.put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
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
			
			Uri calendarURI = client.insert(calendarsURI(), values);
			calendarID = ContentUris.parseId(calendarURI);
		}
	}

	protected void tearDown() throws Exception {
		Uri uri = ContentUris.withAppendedId(calendarsURI(), calendarID);
		client.delete(uri, null, null);
	}

	
	// tests
	
	public void testNothing() {
		assert(true);
	}

	
	// helpers
	
	protected Uri calendarsURI() {
		return Calendars.CONTENT_URI.buildUpon()
			.appendQueryParameter(Calendars.ACCOUNT_NAME, calendarName)
			.appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
			.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true").
		build();
	}

}
