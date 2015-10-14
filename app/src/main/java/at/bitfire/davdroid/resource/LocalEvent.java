/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;

import at.bitfire.ical4android.AndroidCalendar;
import at.bitfire.ical4android.AndroidEvent;
import at.bitfire.ical4android.AndroidEventFactory;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.Event;
import lombok.Getter;
import lombok.Setter;

public class LocalEvent extends AndroidEvent implements LocalResource {

    static final String COLUMN_FILENAME = CalendarContract.Events.SYNC_DATA1,
                        COLUMN_ETAG = CalendarContract.Events.SYNC_DATA2,
                        COLUMN_UID = CalendarContract.Events.UID_2445;

    @Getter protected String fileName;
    @Getter @Setter protected String eTag;

    public LocalEvent(AndroidCalendar calendar, Event event, String fileName, String eTag) {
        super(calendar, event);
        this.fileName = fileName;
        this.eTag = eTag;
    }

    protected LocalEvent(AndroidCalendar calendar, long id, ContentValues baseInfo) {
        super(calendar, id, baseInfo);
        fileName = baseInfo.getAsString(COLUMN_FILENAME);
        eTag = baseInfo.getAsString(COLUMN_ETAG);
    }


    /* process LocalEvent-specific fields */

    @Override
    protected void populateEvent(ContentValues values) {
        super.populateEvent(values);
        fileName = values.getAsString(COLUMN_FILENAME);
        eTag = values.getAsString(COLUMN_ETAG);
        event.uid = values.getAsString(COLUMN_UID);
    }

    @Override
    protected void buildEvent(Event recurrence, ContentProviderOperation.Builder builder) {
        super.buildEvent(recurrence, builder);
        builder .withValue(COLUMN_FILENAME, fileName)
                .withValue(COLUMN_ETAG, eTag)
                .withValue(COLUMN_UID, event.uid);
    }


    /* custom queries */

    public void updateFileNameAndUID(String uid) throws CalendarStorageException {
        try {
            String newFileName = uid + ".ics";

            ContentValues values = new ContentValues(2);
            values.put(COLUMN_FILENAME, newFileName);
            values.put(COLUMN_UID, uid);
            calendar.provider.update(eventSyncURI(), values, null, null);

            fileName = newFileName;
            if (event != null)
                event.uid = uid;

        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't update UID", e);
        }
    }

    @Override
    public void clearDirty(String eTag) throws CalendarStorageException {
        try {
            ContentValues values = new ContentValues(2);
            values.put(CalendarContract.Events.DIRTY, 0);
            values.put(COLUMN_ETAG, eTag);
            calendar.provider.update(eventSyncURI(), values, null, null);

            this.eTag = eTag;
        } catch (RemoteException e) {
            throw new CalendarStorageException("Couldn't update UID", e);
        }
    }


    static class Factory implements AndroidEventFactory {
        static final Factory INSTANCE = new Factory();

        @Override
        public AndroidEvent newInstance(AndroidCalendar calendar, long id, ContentValues baseInfo) {
            return new LocalEvent(calendar, id, baseInfo);
        }

        @Override
        public AndroidEvent newInstance(AndroidCalendar calendar, Event event) {
            return new LocalEvent(calendar, event, null, null);
        }

        @Override
        public AndroidEvent[] newArray(int size) {
            return new LocalEvent[size];
        }
    }
}
