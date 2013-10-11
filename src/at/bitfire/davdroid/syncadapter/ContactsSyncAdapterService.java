/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import lombok.Synchronized;
import net.fortuna.ical4j.data.ParserException;

import org.apache.http.HttpException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.resource.CardDavAddressBook;
import at.bitfire.davdroid.resource.IncapableResourceException;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.RemoteCollection;

public class ContactsSyncAdapterService extends Service {
	private static SyncAdapter syncAdapter;

	@Override @Synchronized
	public void onCreate() {
		if (syncAdapter == null)
			syncAdapter = new SyncAdapter(getApplicationContext());
	}

	@Override
	public IBinder onBind(Intent intent) {
		return syncAdapter.getSyncAdapterBinder();
	}

	private static class SyncAdapter extends AbstractThreadedSyncAdapter {
		private final static String TAG = "davdroid.ContactsSyncAdapter";
		private AccountManager accountManager;

		public SyncAdapter(Context context) {
			super(context, true);
			accountManager = AccountManager.get(context);
		}

		@Override
		public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider,	SyncResult syncResult) {
			Log.i(TAG, "Performing sync for authority " + authority);
			
			String addressBookPath = accountManager.getUserData(account, Constants.ACCOUNT_KEY_ADDRESSBOOK_PATH);
			if (addressBookPath == null)
				return;
			
			try {
				URI uri = new URI(accountManager.getUserData(account, Constants.ACCOUNT_KEY_BASE_URL)).resolve(addressBookPath);
				
				RemoteCollection dav = new CardDavAddressBook(
					uri.toString(),
					accountManager.getUserData(account, Constants.ACCOUNT_KEY_USERNAME),
					accountManager.getPassword(account),
					Boolean.parseBoolean(accountManager.getUserData(account, Constants.ACCOUNT_KEY_AUTH_PREEMPTIVE)));
				
				LocalCollection database = new LocalAddressBook(account, provider, accountManager);
				
				SyncManager syncManager = new SyncManager(account, accountManager);
				syncManager.synchronize(database, dav, extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL), syncResult);
				
			} catch (IOException e) {
				syncResult.stats.numIoExceptions++;
				Log.e(TAG, e.toString());
			} catch (ParserException e) {
				syncResult.stats.numParseExceptions++;
				Log.e(TAG, e.toString());
			} catch (HttpException e) {
				syncResult.stats.numParseExceptions++;
				Log.e(TAG, e.toString());
			} catch (IncapableResourceException e) {
				syncResult.stats.numParseExceptions++;
				Log.e(TAG, e.toString());
			} catch(RemoteException e) {
				syncResult.databaseError = true;
				Log.e(TAG, e.toString());
			} catch(OperationApplicationException e) {
				syncResult.databaseError = true;
				Log.e(TAG, e.toString());
			} catch (URISyntaxException e) {
				syncResult.stats.numIoExceptions++;
				Log.e(TAG, e.toString());
			}
		}
	}
}
