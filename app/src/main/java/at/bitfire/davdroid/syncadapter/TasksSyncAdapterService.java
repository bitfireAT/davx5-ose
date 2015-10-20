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
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.resource.LocalTaskList;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.TaskProvider;
import lombok.Cleanup;

public class TasksSyncAdapterService extends Service {
	private static SyncAdapter syncAdapter;

	@Override
	public void onCreate() {
		if (syncAdapter == null)
			syncAdapter = new SyncAdapter(getApplicationContext());
	}

	@Override
	public void onDestroy() {
		syncAdapter = null;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return syncAdapter.getSyncAdapterBinder(); 
	}
	

	private static class SyncAdapter extends AbstractThreadedSyncAdapter {
        public SyncAdapter(Context context) {
            super(context, false);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient providerClient, SyncResult syncResult) {
            Constants.log.info("Starting task sync (" + authority + ")");

            try {
                @Cleanup TaskProvider provider = TaskProvider.acquire(getContext().getContentResolver(), TaskProvider.ProviderName.OpenTasks);
                if (provider == null)
                    throw new CalendarStorageException("Couldn't access OpenTasks provider");

                for (LocalTaskList taskList : (LocalTaskList[])LocalTaskList.find(account, provider, LocalTaskList.Factory.INSTANCE, null, null)) {
                    Constants.log.info("Synchronizing task list #"  + taskList.getId() + ", URL: " + taskList.getSyncId());
                    TasksSyncManager syncManager = new TasksSyncManager(getContext(), account, extras, authority, provider, syncResult, taskList);
                    syncManager.performSync();
                }
            } catch (CalendarStorageException e) {
                Constants.log.error("Couldn't enumerate local task lists", e);
            }

            Constants.log.info("Task sync complete");
        }
    }

}
