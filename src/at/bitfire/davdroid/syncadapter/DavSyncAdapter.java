package at.bitfire.davdroid.syncadapter;

import java.io.IOException;
import java.util.Map;

import net.fortuna.ical4j.util.SimpleHostInfo;
import net.fortuna.ical4j.util.UidGenerator;

import org.apache.http.HttpException;
import org.apache.http.auth.AuthenticationException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.RemoteCollection;
import at.bitfire.davdroid.webdav.InvalidDavResponseException;

public abstract class DavSyncAdapter extends AbstractThreadedSyncAdapter {
	private final static String TAG = "davdroid.DavSyncAdapter";
	
	protected AccountManager accountManager;
	
	private static String androidID;

	

	public DavSyncAdapter(Context context) {
		super(context, true);
		
		androidID = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
		
		accountManager = AccountManager.get(context);
	}

	
	public static String generateUID() {
		UidGenerator generator = new UidGenerator(new SimpleHostInfo(androidID), String.valueOf(android.os.Process.myPid()));
		return generator.generateUid().getValue();
	}

	
	protected abstract Map<LocalCollection<?>, RemoteCollection<?>> getSyncPairs(Account account, ContentProviderClient provider);
	

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority,	ContentProviderClient provider, SyncResult syncResult) {
		Log.i(TAG, "Performing sync for authority " + authority);
		
		// set class loader for iCal4j ResourceLoader
		Thread.currentThread().setContextClassLoader(getContext().getClassLoader());
		
		SyncManager syncManager = new SyncManager(account, accountManager);
		
		Map<LocalCollection<?>, RemoteCollection<?>> syncCollections = getSyncPairs(account, provider);
		if (syncCollections == null)
			Log.i(TAG, "Nothing to synchronize");
		else
			try {
				for (Map.Entry<LocalCollection<?>, RemoteCollection<?>> entry : syncCollections.entrySet())
					syncManager.synchronize(entry.getKey(), entry.getValue(), extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL), syncResult);
				
			} catch (AuthenticationException ex) {
				syncResult.stats.numAuthExceptions++;
				Log.e(TAG, "HTTP authorization error", ex);
			} catch (InvalidDavResponseException ex) {
				syncResult.stats.numParseExceptions++;
				Log.e(TAG, "Invalid DAV response", ex);
			} catch (HttpException ex) {
				syncResult.stats.numIoExceptions++;
				Log.e(TAG, "HTTP error", ex);
			} catch (OperationApplicationException ex) {
				syncResult.databaseError = true;
				Log.e(TAG, "Content provider operation error", ex);
			} catch (RemoteException ex) {
				syncResult.databaseError = true;
				Log.e(TAG, "Remote process (content provider?) died", ex);
			} catch (IOException ex) {
				syncResult.stats.numIoExceptions++;
				Log.e(TAG, "I/O error", ex);
			}
	}
}
