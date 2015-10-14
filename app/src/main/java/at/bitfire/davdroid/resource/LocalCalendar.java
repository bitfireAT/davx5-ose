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
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.TextUtils;

import com.google.common.base.Joiner;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.component.VTimeZone;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;

import at.bitfire.davdroid.Constants;
import at.bitfire.ical4android.AndroidCalendar;
import at.bitfire.ical4android.AndroidCalendarFactory;
import at.bitfire.ical4android.BatchOperation;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.DateUtils;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;

public class LocalCalendar extends AndroidCalendar implements LocalCollection {

    public static final int defaultColor = 0xFFC3EA6E;     // "DAVdroid green"

    public static final String COLUMN_CTAG = Calendars.CAL_SYNC1;

    protected static final int
            DIRTY_INCREASE_SEQUENCE = 1,
            DIRTY_DONT_INCREASE_SEQUENCE = 2;

    static String[] BASE_INFO_COLUMNS = new String[] {
            Events._ID,
            Events._SYNC_ID,
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

        if (info.isReadOnly())
            values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ);
        else {
            values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
            values.put(Calendars.CAN_MODIFY_TIME_ZONE, 1);
            values.put(Calendars.CAN_ORGANIZER_RESPOND, 1);
        }

        values.put(Calendars.OWNER_ACCOUNT, account.name);
        values.put(Calendars.SYNC_EVENTS, 1);
        values.put(Calendars.VISIBLE, 1);
        if (!TextUtils.isEmpty(info.timezone)) {
            VTimeZone timeZone = DateUtils.parseVTimeZone(info.timezone);
            if (timeZone != null && timeZone.getTimeZoneId() != null)
                values.put(Calendars.CALENDAR_TIME_ZONE, DateUtils.findAndroidTimezoneID(timeZone.getTimeZoneId().getValue()));
        }
        values.put(Calendars.ALLOWED_REMINDERS, Reminders.METHOD_ALERT);
        if (Build.VERSION.SDK_INT >= 15) {
            values.put(Calendars.ALLOWED_AVAILABILITY, Joiner.on(",").join(Reminders.AVAILABILITY_TENTATIVE, Reminders.AVAILABILITY_FREE, Reminders.AVAILABILITY_BUSY));
            values.put(Calendars.ALLOWED_ATTENDEE_TYPES, Joiner.on(",").join(CalendarContract.Attendees.TYPE_OPTIONAL, CalendarContract.Attendees.TYPE_REQUIRED, CalendarContract.Attendees.TYPE_RESOURCE));
        }
        return create(account, provider, values);
    }


    @Override
    public LocalResource[] getAll() throws CalendarStorageException, ContactsStorageException {
        return (LocalEvent[])queryEvents(Events.ORIGINAL_ID + " IS NULL", null);
    }

    @Override
    public LocalEvent[] getDeleted() throws CalendarStorageException {
        return (LocalEvent[])queryEvents(Events.DELETED + "!=0 AND " + Events.ORIGINAL_ID + " IS NULL", null);
    }

    @Override
    public LocalEvent[] getWithoutFileName() throws CalendarStorageException {
        return (LocalEvent[])queryEvents(Events._SYNC_ID + " IS NULL AND " + Events.ORIGINAL_ID + " IS NULL", null);
    }

    @Override
    public LocalResource[] getDirty() throws CalendarStorageException, FileNotFoundException {
        List<LocalResource> dirty = new LinkedList<>();

        // get dirty events which are not required to have an increased SEQUENCE value
        for (LocalEvent event : (LocalEvent[])queryEvents(Events.DIRTY + "=" + DIRTY_DONT_INCREASE_SEQUENCE + " AND " + Events.ORIGINAL_ID + " IS NULL", null))
            dirty.add(event);

        // get dirty events which are required to have an increased SEQUENCE value
        for (LocalEvent event : (LocalEvent[])queryEvents(Events.DIRTY + "=" + DIRTY_INCREASE_SEQUENCE + " AND " + Events.ORIGINAL_ID + " IS NULL", null)) {
            event.getEvent().sequence++;
            dirty.add(event);
        }

        return dirty.toArray(new LocalResource[dirty.size()]);
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

    public void processDirtyExceptions() throws CalendarStorageException {
        // process deleted exceptions
        Constants.log.info("Processing deleted exceptions");
        try {
            @Cleanup Cursor cursor = provider.query(
                    syncAdapterURI(Events.CONTENT_URI),
                    new String[] { Events._ID, Events.ORIGINAL_ID, LocalEvent.COLUMN_SEQUENCE },
                    Events.DELETED + "!=0 AND " + Events.ORIGINAL_ID + " IS NOT NULL", null, null);
            while (cursor != null && cursor.moveToNext()) {
                Constants.log.debug("Found deleted exception, removing; then re-schuling original event");
                long    id = cursor.getLong(0),             // can't be null (by definition)
                        originalID = cursor.getLong(1);     // can't be null (by query)
                int sequence = cursor.isNull(2) ? 0 : cursor.getInt(2);

                // get original event's SEQUENCE
                @Cleanup Cursor cursor2 = provider.query(
                        syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)),
                        new String[] { LocalEvent.COLUMN_SEQUENCE },
                        null, null, null);
                int originalSequence = cursor.isNull(0) ? 0 : cursor.getInt(0);

                BatchOperation batch = new BatchOperation(provider);
                // re-schedule original event and set it to DIRTY
                batch.enqueue(ContentProviderOperation.newUpdate(
                        syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)))
                        .withValue(LocalEvent.COLUMN_SEQUENCE, originalSequence)
                        .withValue(Events.DIRTY, DIRTY_INCREASE_SEQUENCE)
                        .build());
                // remove exception
                batch.enqueue(ContentProviderOperation.newDelete(
                        syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id))).build());
                batch.commit();
            }
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't process locally modified exception", e);
        }

        // process dirty exceptions
        Constants.log.info("Processing dirty exceptions");
        try {
            @Cleanup Cursor cursor = provider.query(
                    syncAdapterURI(Events.CONTENT_URI),
                    new String[] { Events._ID, Events.ORIGINAL_ID, LocalEvent.COLUMN_SEQUENCE },
                    Events.DIRTY + "!=0 AND " + Events.ORIGINAL_ID + " IS NOT NULL", null, null);
            while (cursor != null && cursor.moveToNext()) {
                Constants.log.debug("Found dirty exception, increasing SEQUENCE to re-schedule");
                long    id = cursor.getLong(0),             // can't be null (by definition)
                        originalID = cursor.getLong(1);     // can't be null (by query)
                int sequence = cursor.isNull(2) ? 0 : cursor.getInt(2);

                BatchOperation batch = new BatchOperation(provider);
                // original event to DIRTY
                batch.enqueue(ContentProviderOperation.newUpdate(
                        syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, originalID)))
                        .withValue(Events.DIRTY, DIRTY_DONT_INCREASE_SEQUENCE)
                        .build());
                // increase SEQUENCE and set DIRTY to 0
                batch.enqueue(ContentProviderOperation.newUpdate(
                                syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, id)))
                                .withValue(LocalEvent.COLUMN_SEQUENCE, sequence + 1)
                                .withValue(Events.DIRTY, 0)
                                .build());
                batch.commit();
            }
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't process locally modified exception", e);
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
