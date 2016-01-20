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

import java.util.*;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB.*;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import at.bitfire.davdroid.resource.LocalTaskList;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.TaskProvider;
import lombok.Cleanup;

public class TasksSyncAdapterService extends Service {
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
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient providerClient, SyncResult syncResult) {
            Constants.log.info("Starting task sync (" + authority + ")");

            try {
                @Cleanup TaskProvider provider = TaskProvider.acquire(getContext().getContentResolver(), TaskProvider.ProviderName.OpenTasks);
                if (provider == null)
                    throw new CalendarStorageException("Couldn't access OpenTasks provider");

                updateLocalTaskLists(provider, account);

                for (LocalTaskList taskList : (LocalTaskList[])LocalTaskList.find(account, provider, LocalTaskList.Factory.INSTANCE, null, null)) {
                    Constants.log.info("Synchronizing task list #"  + taskList.getId() + ", URL: " + taskList.getSyncId());
                    TasksSyncManager syncManager = new TasksSyncManager(getContext(), account, extras, authority, provider, syncResult, taskList);
                    syncManager.performSync();
                }
            } catch (CalendarStorageException e) {
                Constants.log.error("Couldn't enumerate local task lists", e);
            } finally {
                db.close();
            }

            Constants.log.info("Task sync complete");
        }

        private void updateLocalTaskLists(TaskProvider provider, Account account) throws CalendarStorageException {
            long service = getService(account);

            // enumerate remote and local task lists
            Map<String, CollectionInfo> remote = remoteTaskLists(service);
            LocalTaskList[] local = (LocalTaskList[])LocalTaskList.find(account, provider, LocalTaskList.Factory.INSTANCE, null, null);

            // delete obsolete local task lists
            for (LocalTaskList list : local) {
                String url = list.getSyncId();
                if (!remote.containsKey(url)) {
                    Constants.log.debug("Deleting obsolete local task list {}", url);
                    list.delete();
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    CollectionInfo info = remote.get(url);
                    Constants.log.debug("Updating local task list {} with {}", url, info);
                    list.update(info);
                    // we already have a local task list for this remote collection, don't take into consideration anymore
                    remote.remove(url);
                }
            }

            // create new local task lists
            for (String url : remote.keySet()) {
                CollectionInfo info = remote.get(url);
                Constants.log.info("Adding local task list {}", info);
                LocalTaskList.create(account, provider, info);
            }
        }

        long getService(Account account) {
            @Cleanup Cursor c = db.query(Services._TABLE, new String[]{ Services.ID },
                    Services.ACCOUNT_NAME + "=? AND " + Services.SERVICE + "=?", new String[]{ account.name, Services.SERVICE_CALDAV }, null, null, null);
            c.moveToNext();
            return c.getLong(0);
        }

        private Map<String, CollectionInfo> remoteTaskLists(long service) {
            Map<String, CollectionInfo> collections = new LinkedHashMap<>();
            @Cleanup Cursor cursor = db.query(Collections._TABLE, Collections._COLUMNS,
                    Collections.SERVICE_ID + "=? AND " + Collections.SUPPORTS_VTODO + "!=0 AND selected",
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
