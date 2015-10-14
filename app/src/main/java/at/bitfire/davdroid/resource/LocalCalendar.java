/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;

import com.google.common.base.Joiner;

import at.bitfire.ical4android.AndroidCalendar;
import at.bitfire.ical4android.AndroidCalendarFactory;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;

public class LocalCalendar extends AndroidCalendar implements LocalCollection {

    public static final int defaultColor = 0xFFC3EA6E;     // "DAVdroid green"

    public static final String COLUMN_CTAG = Calendars.CAL_SYNC1;

    static String[] BASE_INFO_COLUMNS = new String[] {
            Events._ID,
            LocalEvent.COLUMN_FILENAME,
            LocalEvent.COLUMN_ETAG
    };

    @Override
    protected String[] eventBaseInfoColumns() {
        return BASE_INFO_COLUMNS;
    }


    protected LocalCalendar(Account account, ContentProviderClient provider, long id) {
        super(account, provider, LocalEvent.Factory.INSTANCE, id);
    }

    public static Uri create(Account account, ContentResolver resolver, ServerInfo.ResourceInfo info) throws CalendarStorageException {
        @Cleanup("release") ContentProviderClient provider = resolver.acquireContentProviderClient(CalendarContract.AUTHORITY);
        if (provider == null)
            throw new CalendarStorageException("Couldn't acquire ContentProviderClient for " + CalendarContract.AUTHORITY);

        ContentValues values = new ContentValues();
        values.put(Calendars.NAME, info.getURL());
        values.put(Calendars.CALENDAR_DISPLAY_NAME, info.getTitle());
        values.put(Calendars.CALENDAR_COLOR, info.color != null ? info.color : defaultColor);
        values.put(Calendars.CALENDAR_ACCESS_LEVEL, info.readOnly ? Calendars.CAL_ACCESS_READ : Calendars.CAL_ACCESS_OWNER);
        values.put(Calendars.OWNER_ACCOUNT, account.name);
        values.put(Calendars.SYNC_EVENTS, 1);
        if (info.timezone != null) {
            // TODO parse VTIMEZONE
            // values.put(Calendars.CALENDAR_TIME_ZONE, DateUtils.findAndroidTimezoneID(info.timezone));
        }
        values.put(Calendars.ALLOWED_REMINDERS, Reminders.METHOD_ALERT);
        values.put(Calendars.ALLOWED_AVAILABILITY, Joiner.on(",").join(Reminders.AVAILABILITY_TENTATIVE, Reminders.AVAILABILITY_FREE, Reminders.AVAILABILITY_BUSY));
        values.put(Calendars.ALLOWED_ATTENDEE_TYPES, Joiner.on(",").join(CalendarContract.Attendees.TYPE_OPTIONAL, CalendarContract.Attendees.TYPE_REQUIRED, CalendarContract.Attendees.TYPE_RESOURCE));
        return create(account, provider, values);
    }


    @Override
    public LocalResource[] getAll() throws CalendarStorageException, ContactsStorageException {
        return (LocalEvent[])queryEvents(null, null);
    }

    @Override
    public LocalEvent[] getDeleted() throws CalendarStorageException {
        return (LocalEvent[])queryEvents(Events.DELETED + "!=0", null);
    }

    @Override
    public LocalEvent[] getWithoutFileName() throws CalendarStorageException {
        return (LocalEvent[])queryEvents(LocalEvent.COLUMN_FILENAME + " IS NULL", null);
    }

    @Override
    public LocalResource[] getDirty() throws CalendarStorageException {
        return (LocalEvent[])queryEvents(Events.DIRTY + "!=0", null);
    }


    @Override
    public String getCTag() throws CalendarStorageException {
        try {
            @Cleanup Cursor cursor = provider.query(calendarSyncURI(), new String[] { COLUMN_CTAG }, null, null, null);
            if (cursor != null && cursor.moveToNext())
                return cursor.getString(0);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't read local (last known) CTag", e);
        }
        return null;
    }

    @Override
    public void setCTag(String cTag) throws CalendarStorageException, ContactsStorageException {
        try {
            ContentValues values = new ContentValues(1);
            values.put(COLUMN_CTAG, cTag);
            provider.update(calendarSyncURI(), values, null, null);
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't write local (last known) CTag", e);
        }
    }


    public static class Factory implements AndroidCalendarFactory {
        public static final Factory INSTANCE = new Factory();

        @Override
        public AndroidCalendar newInstance(Account account, ContentProviderClient provider, long id) {
            return new LocalCalendar(account, provider, id);
        }

        @Override
        public AndroidCalendar[] newArray(int size) {
            return new LocalCalendar[size];
        }
    }
}
