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
import android.database.Cursor;
import android.os.Build;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.support.annotation.NonNull;

import net.fortuna.ical4j.model.property.ProdId;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.UUID;

import at.bitfire.davdroid.BuildConfig;
import at.bitfire.ical4android.AndroidCalendar;
import at.bitfire.ical4android.AndroidEvent;
import at.bitfire.ical4android.AndroidEventFactory;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.Event;
import lombok.Cleanup;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@TargetApi(17)
@ToString(of={ "fileName","eTag" }, callSuper=true)
public class LocalEvent extends AndroidEvent implements LocalResource {
    static {
        Event.setProdId(new ProdId("+//IDN bitfire.at//DAVdroid/" + BuildConfig.VERSION_NAME + " ical4j/2.x"));
    }

    static final String COLUMN_ETAG = CalendarContract.Events.SYNC_DATA1,
                        COLUMN_UID = Build.VERSION.SDK_INT >= 17 ? Events.UID_2445 : Events.SYNC_DATA2,
                        COLUMN_SEQUENCE = CalendarContract.Events.SYNC_DATA3;

    @Getter protected String fileName;
    @Getter @Setter protected String eTag;

    public boolean weAreOrganizer = true;

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
    protected void populateEvent(ContentValues values) throws FileNotFoundException, CalendarStorageException {
        super.populateEvent(values);
        fileName = values.getAsString(Events._SYNC_ID);
        eTag = values.getAsString(COLUMN_ETAG);
        getEvent().setUid(values.getAsString(COLUMN_UID));

        getEvent().setSequence(values.getAsInteger(COLUMN_SEQUENCE));
        if (Build.VERSION.SDK_INT >= 17) {
            Integer isOrganizer = values.getAsInteger(Events.IS_ORGANIZER);
            weAreOrganizer = isOrganizer != null && isOrganizer != 0;
        } else {
            String organizer = values.getAsString(Events.ORGANIZER);
            weAreOrganizer = organizer == null || organizer.equals(getCalendar().getAccount().name);
        }
    }

    @Override
    protected void buildEvent(Event recurrence, ContentProviderOperation.Builder builder) throws FileNotFoundException, CalendarStorageException {
        super.buildEvent(recurrence, builder);

        boolean buildException = recurrence != null;
        Event eventToBuild = buildException ? recurrence : getEvent();

        builder .withValue(COLUMN_UID, getEvent().getUid())
                .withValue(COLUMN_SEQUENCE, eventToBuild.getSequence())
                .withValue(CalendarContract.Events.DIRTY, 0)
                .withValue(CalendarContract.Events.DELETED, 0);

        if (buildException)
            builder.withValue(Events.ORIGINAL_SYNC_ID, fileName);
        else
            builder .withValue(Events._SYNC_ID, fileName)
                    .withValue(COLUMN_ETAG, eTag);
    }


    /* custom queries */

    public void prepareForUpload() throws CalendarStorageException {
        try {
            String uid = null;
            @Cleanup Cursor c = getCalendar().getProvider().query(eventSyncURI(), new String[] { COLUMN_UID }, null, null, null);
            if (c.moveToNext())
                uid = c.getString(0);
            if (uid == null)
                uid = UUID.randomUUID().toString();

            final String newFileName = uid + ".ics";

            ContentValues values = new ContentValues(2);
            values.put(Events._SYNC_ID, newFileName);
            values.put(COLUMN_UID, uid);
            getCalendar().getProvider().update(eventSyncURI(), values, null, null);

            fileName = newFileName;
            if (getEvent() != null)
                getEvent().setUid(uid);

        } catch (FileNotFoundException|RemoteException e) {
            throw new CalendarStorageException("Couldn't update UID", e);
        }
    }

    @Override
    public void clearDirty(String eTag) throws CalendarStorageException {
        try {
            ContentValues values = new ContentValues(2);
            values.put(CalendarContract.Events.DIRTY, 0);
            values.put(COLUMN_ETAG, eTag);
            if (getEvent() != null)
                values.put(COLUMN_SEQUENCE, getEvent().getSequence());
            getCalendar().getProvider().update(eventSyncURI(), values, null, null);

            this.eTag = eTag;
        } catch (IOException|RemoteException e) {
            throw new CalendarStorageException("Couldn't update UID", e);
        }
    }


    static class Factory implements AndroidEventFactory<LocalEvent> {
        static final Factory INSTANCE = new Factory();

        @Override
        public LocalEvent newInstance(AndroidCalendar calendar, long id, ContentValues baseInfo) {
            return new LocalEvent(calendar, id, baseInfo);
        }

        @Override
        public LocalEvent newInstance(AndroidCalendar calendar, Event event) {
            return new LocalEvent(calendar, event, null, null);
        }

    }
}
