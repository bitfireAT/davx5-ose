/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.app.Service;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import at.bitfire.davdroid.resource.CalDavTaskList;
import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.LocalTaskList;
import at.bitfire.davdroid.resource.RemoteCollection;

public class TasksSyncAdapterService extends Service {
	private static SyncAdapter syncAdapter;

	@Override
	public void onCreate() {
		if (syncAdapter == null)
			syncAdapter = new SyncAdapter(getApplicationContext());
	}

	@Override
	public void onDestroy() {
		syncAdapter.close();
		syncAdapter = null;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return syncAdapter.getSyncAdapterBinder(); 
	}
	

	private static class SyncAdapter extends DavSyncAdapter {
		private final static String TAG = "davdroid.TasksSync";

		private SyncAdapter(Context context) {
			super(context);
		}
		
		@Override
		protected Map<LocalCollection<?>, RemoteCollection<?>> getSyncPairs(Account account, ContentProviderClient provider) {
			AccountSettings settings = new AccountSettings(getContext(), account);
			String	userName = settings.getUserName(),
					password = settings.getPassword();
			boolean preemptive = settings.getPreemptiveAuth();

			try {
				Map<LocalCollection<?>, RemoteCollection<?>> map = new HashMap<LocalCollection<?>, RemoteCollection<?>>();

				for (LocalTaskList calendar : LocalTaskList.findAll(account, provider)) {
					RemoteCollection<?> dav = new CalDavTaskList(httpClient, calendar.getUrl(), userName, password, preemptive);
					map.put(calendar, dav);
				}
				return map;
			} catch (RemoteException ex) {
				Log.e(TAG, "Couldn't find local task lists", ex);
			} catch (URISyntaxException ex) {
				Log.e(TAG, "Couldn't build task list URI", ex);
			}
			
			return null;
		}
	}
}
