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

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.ServiceDB.OpenHelper;
import at.bitfire.davdroid.resource.LocalCalendar;
import at.bitfire.ical4android.CalendarStorageException;
import lombok.Cleanup;

public class CalendarsSyncAdapterService extends Service {
    private static SyncAdapter syncAdapter;
    OpenHelper dbHelper;

    @Override
    public void onCreate() {
        dbHelper = new OpenHelper(this);
        syncAdapter = new SyncAdapter(this, dbHelper);
    }

    @Override
    public void onDestroy() {
        dbHelper.close();
    }

	@Override
	public IBinder onBind(Intent intent) {
		return syncAdapter.getSyncAdapterBinder(); 
	}
	

	private static class SyncAdapter extends AbstractThreadedSyncAdapter {
        private final OpenHelper dbHelper;
        private final SQLiteDatabase db;

        public SyncAdapter(Context context, OpenHelper dbHelper) {
            super(context, false);

            this.dbHelper = dbHelper;
            db = dbHelper.getReadableDatabase();
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            Constants.log.info("Starting calendar sync (" + authority + ")");

            try {
                updateLocalCalendars(provider, account);

                for (LocalCalendar calendar : (LocalCalendar[])LocalCalendar.find(account, provider, LocalCalendar.Factory.INSTANCE, CalendarContract.Calendars.SYNC_EVENTS + "!=0", null)) {
                    Constants.log.info("Synchronizing calendar #"  + calendar.getId() + ", URL: " + calendar.getName());
                    CalendarSyncManager syncManager = new CalendarSyncManager(getContext(), account, extras, authority, syncResult, calendar);
                    syncManager.performSync();
                }
            } catch (CalendarStorageException e) {
                Constants.log.error("Couldn't enumerate local calendars", e);
            } finally {
                dbHelper.close();
            }

            Constants.log.info("Calendar sync complete");
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
                    Constants.log.debug("Deleting obsolete local calendar {}", url);
                    calendar.delete();
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    CollectionInfo info = remote.get(url);
                    Constants.log.debug("Updating local calendar {} with {}", url, info);
                    calendar.update(info);
                    // we already have a local calendar for this remote collection, don't take into consideration anymore
                    remote.remove(url);
                }
            }

            // create new local calendars
            for (String url : remote.keySet()) {
                CollectionInfo info = remote.get(url);
                Constants.log.info("Adding local calendar list {}", info);
                LocalCalendar.create(account, provider, info);
            }
        }

        long getService(Account account) {
            @Cleanup Cursor c = db.query(ServiceDB.Services._TABLE, new String[]{ServiceDB.Services.ID},
                    ServiceDB.Services.ACCOUNT_NAME + "=? AND " + ServiceDB.Services.SERVICE + "=?", new String[]{account.name, ServiceDB.Services.SERVICE_CALDAV}, null, null, null);
            c.moveToNext();
            return c.getLong(0);
        }

        private Map<String, CollectionInfo> remoteCalendars(long service) {
            Map<String, CollectionInfo> collections = new LinkedHashMap<>();
            @Cleanup Cursor cursor = db.query(ServiceDB.Collections._TABLE, ServiceDB.Collections._COLUMNS,
                    ServiceDB.Collections.SERVICE_ID + "=? AND " + ServiceDB.Collections.SUPPORTS_VEVENT + "!=0",
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
