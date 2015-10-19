/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import android.annotation.TargetApi;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.os.Build;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;

import net.fortuna.ical4j.model.property.ProdId;

import at.bitfire.davdroid.BuildConfig;
import at.bitfire.ical4android.AndroidCalendar;
import at.bitfire.ical4android.AndroidEvent;
import at.bitfire.ical4android.AndroidEventFactory;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.Event;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@TargetApi(17)
public class LocalEvent extends AndroidEvent implements LocalResource {
    static {
        Event.prodId = new ProdId("+//IDN bitfire.at//DAVdroid/" + BuildConfig.VERSION_NAME + " ical4android ical4j/2.x");
    }

    static final String COLUMN_ETAG = CalendarContract.Events.SYNC_DATA1,
                        COLUMN_UID = Build.VERSION.SDK_INT >= 17 ? Events.UID_2445 : Events.SYNC_DATA2,
                        COLUMN_SEQUENCE = CalendarContract.Events.SYNC_DATA3;

    @Getter protected String fileName;
    @Getter @Setter protected String eTag;

    public LocalEvent(@NonNull AndroidCalendar calendar, Event event, String fileName, String eTag) {
        super(calendar, event);
        this.fileName = fileName;
        this.eTag = eTag;
    }

    protected LocalEvent(@NonNull AndroidCalendar calendar, long id, ContentValues baseInfo) {
        super(calendar, id, baseInfo);
        if (baseInfo != null) {
            fileName = baseInfo.getAsString(Events._SYNC_ID);
            eTag = baseInfo.getAsString(COLUMN_ETAG);
        }
    }


    /* process LocalEvent-specific fields */

    @Override
    protected void populateEvent(ContentValues values) {
        super.populateEvent(values);
        fileName = values.getAsString(Events._SYNC_ID);
        eTag = values.getAsString(COLUMN_ETAG);
        event.uid = values.getAsString(COLUMN_UID);

        event.sequence = values.getAsInteger(COLUMN_SEQUENCE);
    }

    @Override
    protected void buildEvent(Event recurrence, ContentProviderOperation.Builder builder) {
        super.buildEvent(recurrence, builder);

        boolean buildException = recurrence != null;
        Event eventToBuild = buildException ? recurrence : event;

        builder .withValue(COLUMN_UID, event.uid)
                .withValue(COLUMN_SEQUENCE, eventToBuild.sequence)
                .withValue(CalendarContract.Events.DIRTY, 0)
                .withValue(CalendarContract.Events.DELETED, 0);

        if (buildException)
            builder.withValue(Events.ORIGINAL_SYNC_ID, fileName);
        else
            builder .withValue(Events._SYNC_ID, fileName)
                    .withValue(COLUMN_ETAG, eTag);
    }


    /* custom queries */

    public void updateFileNameAndUID(String uid) throws CalendarStorageException {
        try {
            String newFileName = uid + ".ics";

            ContentValues values = new ContentValues(2);
            values.put(Events._SYNC_ID, newFileName);
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
            if (event != null)
                values.put(COLUMN_SEQUENCE, event.sequence);
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
