/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.Closeable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.net.ssl.SSLException;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.LocalStorageException;
import at.bitfire.davdroid.resource.RemoteCollection;
import at.bitfire.davdroid.ui.settings.AccountActivity;
import at.bitfire.davdroid.webdav.DavException;
import at.bitfire.davdroid.webdav.DavHttpClient;
import at.bitfire.davdroid.webdav.HttpException;
import lombok.Getter;

public abstract class DavSyncAdapter extends AbstractThreadedSyncAdapter implements Closeable {
	private final static String TAG = "davdroid.DavSyncAdapter";
	
	@Getter private static String androidID;

	final protected Context context;

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

		this.context = context;
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

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void onPerformSync(Account account, Bundle extras, String authority,	ContentProviderClient provider, SyncResult syncResult) {
		Log.i(TAG, "Performing sync for authority " + authority);
		
		// set class loader for iCal4j ResourceLoader
		Thread.currentThread().setContextClassLoader(getContext().getClassLoader());
		
		// create httpClient, if necessary
		httpClientLock.writeLock().lock();
		if (httpClient == null) {
			Log.d(TAG, "Creating new DavHttpClient");
			httpClient = DavHttpClient.create();
		}
		
		// prevent httpClient shutdown until we're ready by holding a read lock
		// acquiring read lock before releasing write lock will downgrade the write lock to a read lock
		httpClientLock.readLock().lock();
		httpClientLock.writeLock().unlock();

		Exception exceptionToShow = null;     // exception to show notification for
		Intent exceptionIntent = null;        // what shall happen when clicking on the exception notification
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
					exceptionToShow = ex;

				} catch (HttpException ex) {
					if (ex.getCode() == HttpStatus.SC_UNAUTHORIZED) {
						Log.e(TAG, "HTTP Unauthorized " + ex.getCode(), ex);
						syncResult.stats.numAuthExceptions++;   // hard error

						exceptionToShow = ex;
						exceptionIntent = new Intent(context, AccountActivity.class);
						exceptionIntent.putExtra(AccountActivity.EXTRA_ACCOUNT, account);
					} else if (ex.isClientError()) {
						Log.e(TAG, "Hard HTTP error " + ex.getCode(), ex);
						syncResult.stats.numParseExceptions++;  // hard error
						exceptionToShow = ex;
					} else {
						Log.w(TAG, "Soft HTTP error " + ex.getCode() + " (Android will try again later)", ex);
						syncResult.stats.numIoExceptions++;     // soft error
					}
				} catch (LocalStorageException ex) {
					syncResult.databaseError = true;    // hard error
					Log.e(TAG, "Local storage (content provider) exception", ex);
					exceptionToShow = ex;
				} catch (IOException ex) {
					syncResult.stats.numIoExceptions++;     // soft error
					Log.e(TAG, "I/O error (Android will try again later)", ex);
					if (ex instanceof SSLException)         // always notify on SSL/TLS errors
						exceptionToShow = ex;
				} catch (URISyntaxException ex) {
					syncResult.stats.numParseExceptions++;  // hard error
					Log.e(TAG, "Invalid URI (file name) syntax", ex);
					exceptionToShow = ex;
				}
		} finally {
			// allow httpClient shutdown
			httpClientLock.readLock().unlock();
		}

		// show sync errors as notification
		if (exceptionToShow != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			if (exceptionIntent == null)
				exceptionIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.WEB_URL_VIEW_LOGS));

			PendingIntent contentIntent = PendingIntent.getActivity(context, 0, exceptionIntent, 0);
			Notification.Builder builder = new Notification.Builder(context)
					.setSmallIcon(R.drawable.ic_launcher)
					.setPriority(Notification.PRIORITY_LOW)
					.setOnlyAlertOnce(true)
					.setWhen(System.currentTimeMillis())
					.setContentTitle(context.getString(R.string.sync_error_title))
					.setContentText(exceptionToShow.getLocalizedMessage())
					.setContentInfo(account.name)
					.setStyle(new Notification.BigTextStyle().bigText(account.name + ":\n" + ExceptionUtils.getStackTrace(exceptionToShow)))
					.setContentIntent(contentIntent);

			NotificationManager notificationManager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(account.name.hashCode(), builder.build());
		}

		Log.i(TAG, "Sync complete for " + authority);
	}

}
