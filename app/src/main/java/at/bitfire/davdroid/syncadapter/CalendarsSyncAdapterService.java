/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.CalendarContract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import at.bitfire.davdroid.model.ServiceDB.OpenHelper;
import at.bitfire.davdroid.model.ServiceDB.Services;
import at.bitfire.davdroid.resource.LocalCalendar;
import at.bitfire.ical4android.CalendarStorageException;
import lombok.Cleanup;

public class CalendarsSyncAdapterService extends SyncAdapterService {

    @Override
    public void onCreate() {
        super.onCreate();
        syncAdapter = new SyncAdapter(this, dbHelper);
    }


	private static class SyncAdapter extends SyncAdapterService.SyncAdapter {

        public SyncAdapter(Context context, OpenHelper dbHelper) {
            super(context, dbHelper);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            super.onPerformSync(account, extras, authority, provider, syncResult);

            try {
                updateLocalCalendars(provider, account);

                for (LocalCalendar calendar : (LocalCalendar[])LocalCalendar.find(account, provider, LocalCalendar.Factory.INSTANCE, CalendarContract.Calendars.SYNC_EVENTS + "!=0", null)) {
                    App.log.info("Synchronizing calendar #"  + calendar.getId() + ", URL: " + calendar.getName());
                    CalendarSyncManager syncManager = new CalendarSyncManager(getContext(), account, extras, authority, syncResult, calendar);
                    syncManager.performSync();
                }
            } catch (CalendarStorageException e) {
                App.log.log(Level.SEVERE, "Couldn't enumerate local calendars", e);
            }

            App.log.info("Calendar sync complete");
        }

        private void updateLocalCalendars(ContentProviderClient provider, Account account) throws CalendarStorageException {
            long service = getService(account);

            // enumerate remote and local calendars
            Map<String, CollectionInfo> remote = remoteCalendars(service);
            LocalCalendar[] local = (LocalCalendar[])LocalCalendar.find(account, provider, LocalCalendar.Factory.INSTANCE, null, null);

            // delete obsolete local calendar
            for (LocalCalendar calendar : local) {
                String url = calendar.getName();
                if (!remote.containsKey(url)) {
                    App.log.fine("Deleting obsolete local calendar " + url);
                    calendar.delete();
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    CollectionInfo info = remote.get(url);
                    App.log.fine("Updating local calendar " + url + " with " + info);
                    calendar.update(info);
                    // we already have a local calendar for this remote collection, don't take into consideration anymore
                    remote.remove(url);
                }
            }

            // create new local calendars
            for (String url : remote.keySet()) {
                CollectionInfo info = remote.get(url);
                App.log.info("Adding local calendar list " + info);
                LocalCalendar.create(account, provider, info);
            }
        }

        long getService(Account account) {
            @Cleanup Cursor c = db.query(Services._TABLE, new String[]{ Services.ID },
                    Services.ACCOUNT_NAME + "=? AND " + Services.SERVICE + "=?", new String[]{account.name, Services.SERVICE_CALDAV}, null, null, null);
            c.moveToNext();
            return c.getLong(0);
        }

        private Map<String, CollectionInfo> remoteCalendars(long service) {
            Map<String, CollectionInfo> collections = new LinkedHashMap<>();
            @Cleanup Cursor cursor = db.query(Collections._TABLE, null,
                    Collections.SERVICE_ID + "=? AND " + Collections.SUPPORTS_VEVENT + "!=0 AND " + Collections.SYNC,
                    new String[] { String.valueOf(service) }, null, null, null);
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                CollectionInfo info = CollectionInfo.fromDB(values);
                collections.put(info.url, info);
            }
            return collections;
        }
    }

}
