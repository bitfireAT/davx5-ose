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

import at.bitfire.davdroid.resource.CalDavNotebook;
import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.LocalNotebook;
import at.bitfire.davdroid.resource.RemoteCollection;

public class NotesSyncAdapterService extends Service {
	private static NotesSyncAdapter syncAdapter;

	@Override
	public void onCreate() {
		if (syncAdapter == null)
			syncAdapter = new NotesSyncAdapter(getApplicationContext());
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


	private static class NotesSyncAdapter extends DavSyncAdapter {
		private final static String TAG = "davdroid.NotesSync";

		private NotesSyncAdapter(Context context) {
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

				for (LocalNotebook noteList : LocalNotebook.findAll(account, provider)) {
					RemoteCollection<?> dav = new CalDavNotebook(httpClient, noteList.getUrl(), userName, password, preemptive);
					map.put(noteList, dav);
				}
				return map;
			} catch (RemoteException ex) {
				Log.e(TAG, "Couldn't find local notebooks", ex);
			} catch (URISyntaxException ex) {
				Log.e(TAG, "Couldn't build calendar URI", ex);
			}

			return null;
		}
	}
}
