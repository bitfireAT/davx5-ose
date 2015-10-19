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
import android.provider.CalendarContract;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.resource.LocalCalendar;
import at.bitfire.ical4android.CalendarStorageException;

public class CalendarsSyncAdapterService extends Service {
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
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            Constants.log.info("Starting calendar sync (" + authority + ")");

            try {
                for (LocalCalendar calendar : (LocalCalendar[])LocalCalendar.find(account, provider, LocalCalendar.Factory.INSTANCE, CalendarContract.Calendars.SYNC_EVENTS + "!=0", null)) {
                    Constants.log.info("Synchronizing calendar #"  + calendar.getId() + ", URL: " + calendar.getName());
                    CalendarSyncManager syncManager = new CalendarSyncManager(getContext(), account, extras, authority, syncResult, calendar);
                    syncManager.performSync();
                }
            } catch (CalendarStorageException e) {
                Constants.log.error("Couldn't enumerate local calendars", e);
            }

            Constants.log.info("Calendar sync complete");
        }
    }

}
