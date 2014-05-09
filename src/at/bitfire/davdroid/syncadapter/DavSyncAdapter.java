/*******************************************************************************
 * Copyright (c) 2014 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Richard Hirner (bitfire web engineering) - initial API and implementation
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import lombok.Getter;

import org.apache.http.HttpStatus;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.LocalStorageException;
import at.bitfire.davdroid.resource.RemoteCollection;
import at.bitfire.davdroid.webdav.DavException;
import at.bitfire.davdroid.webdav.DavHttpClient;
import at.bitfire.davdroid.webdav.HttpException;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;

public abstract class DavSyncAdapter extends AbstractThreadedSyncAdapter implements Closeable {
	private final static String TAG = "davdroid.DavSyncAdapter";
	
	@Getter private static String androidID;
	
	protected AccountManager accountManager;
	
	/* We use one static httpClient for
	 *   - all sync adapters  (CalendarsSyncAdapter, ContactsSyncAdapter)
	 *   - and all threads (= accounts) of each sync adapter
	 * so that HttpClient's threaded pool management can do its best.
	 */
	protected static CloseableHttpClient httpClient;

	/* One static read/write lock pair for the static httpClient:
	 *    Use the READ  lock when httpClient will only be called (to prevent it from being unset while being used).
	 *    Use the WRITE lock when httpClient will be modified (set/unset). */
	private final static ReentrantReadWriteLock httpClientLock = new ReentrantReadWriteLock();

	
	public DavSyncAdapter(Context context) {
		super(context, true);
		
		synchronized(this) {
			if (androidID == null)
				androidID = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
		}
		
		accountManager = AccountManager.get(context);
	}
	
	@Override
	public void close() {
		Log.d(TAG, "Closing httpClient");
		
		// may be called from a GUI thread, so we need an AsyncTask
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				try {
					httpClientLock.writeLock().lock();
					if (httpClient != null) {
						httpClient.close();
						httpClient = null;
					}
					httpClientLock.writeLock().unlock();
				} catch (IOException e) {
					Log.w(TAG, "Couldn't close HTTP client", e);
				}
				return null;
			}
		}.execute();
	}
	
	protected abstract Map<LocalCollection<?>, RemoteCollection<?>> getSyncPairs(Account account, ContentProviderClient provider);
	

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority,	ContentProviderClient provider, SyncResult syncResult) {
		Log.i(TAG, "Performing sync for authority " + authority);
		
		// set class loader for iCal4j ResourceLoader
		Thread.currentThread().setContextClassLoader(getContext().getClassLoader());
		
		// create httpClient, if necessary
		httpClientLock.writeLock().lock();
		if (httpClient == null) {
			Log.d(TAG, "Creating new DavHttpClient");
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getContext());
			httpClient = DavHttpClient.create(
				settings.getBoolean(Constants.SETTING_DISABLE_COMPRESSION, false),
				settings.getBoolean(Constants.SETTING_NETWORK_LOGGING, false)
			);
		}
		
		// prevent httpClient shutdown until we're ready by holding a read lock
		// acquiring read lock before releasing write lock will downgrade the write lock to a read lock
		httpClientLock.readLock().lock();
		httpClientLock.writeLock().unlock();
		
		try {	
			// get local <-> remote collection pairs
			Map<LocalCollection<?>, RemoteCollection<?>> syncCollections = getSyncPairs(account, provider);
			if (syncCollections == null)
				Log.i(TAG, "Nothing to synchronize");
			else
				try {
					for (Map.Entry<LocalCollection<?>, RemoteCollection<?>> entry : syncCollections.entrySet())
						new SyncManager(entry.getKey(), entry.getValue()).synchronize(extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL), syncResult);
					
				} catch (DavException ex) {
					syncResult.stats.numParseExceptions++;
					Log.e(TAG, "Invalid DAV response", ex);
					
				} catch (HttpException ex) {
					if (ex.getCode() == HttpStatus.SC_UNAUTHORIZED) {
						Log.e(TAG, "HTTP Unauthorized " + ex.getCode(), ex);
						syncResult.stats.numAuthExceptions++;
					} else if (ex.isClientError()) {
						Log.e(TAG, "Hard HTTP error " + ex.getCode(), ex);
						syncResult.stats.numParseExceptions++;
					} else {
						Log.w(TAG, "Soft HTTP error " + ex.getCode() + " (Android will try again later)", ex);
						syncResult.stats.numIoExceptions++;
					}
					
				} catch (LocalStorageException ex) {
					syncResult.databaseError = true;
					Log.e(TAG, "Local storage (content provider) exception", ex);
				} catch (IOException ex) {
					syncResult.stats.numIoExceptions++;
					Log.e(TAG, "I/O error (Android will try again later)", ex);
				}
		} finally {
			// allow httpClient shutdown
			httpClientLock.readLock().unlock();
		}
		
		Log.i(TAG, "Sync complete for " + authority);
	}

}
