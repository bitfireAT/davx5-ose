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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.List;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import at.bitfire.davdroid.model.ServiceDB.OpenHelper;
import lombok.Cleanup;

public class ContactsSyncAdapterService extends Service {
	private static ContactsSyncAdapter syncAdapter;
    private OpenHelper dbHelper;

	@Override
	public void onCreate() {
        dbHelper = new OpenHelper(this);
        syncAdapter = new ContactsSyncAdapter(this, dbHelper.getReadableDatabase());
	}

	@Override
	public void onDestroy() {
		dbHelper.close();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return syncAdapter.getSyncAdapterBinder();
	}
	

	private static class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {
        private final SQLiteDatabase db;

        public ContactsSyncAdapter(Context context, @NonNull SQLiteDatabase db) {
            super(context, false);
            this.db = db;
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            App.log.info("Starting address book sync (" + authority + ")");

            // required for dav4android (ServiceLoader)
            Thread.currentThread().setContextClassLoader(getContext().getClassLoader());

            Long service = getService(account);
            if (service != null) {
                CollectionInfo remote = remoteAddressBook(service);
                if (remote != null) {
                    ContactsSyncManager syncManager = new ContactsSyncManager(getContext(), account, extras, authority, provider, syncResult, remote);
                    syncManager.performSync();
                } else
                    App.log.info("No address book collection selected for synchronization");
            }

            App.log.info("Address book sync complete");
        }

        @Nullable
        private Long getService(@NonNull Account account) {
            @Cleanup Cursor c = db.query(ServiceDB.Services._TABLE, new String[] { ServiceDB.Services.ID },
                    ServiceDB.Services.ACCOUNT_NAME + "=? AND " + ServiceDB.Services.SERVICE + "=?", new String[] { account.name, ServiceDB.Services.SERVICE_CARDDAV }, null, null, null);
            if (c.moveToNext())
                return c.getLong(0);
            else
                return null;
        }

        @Nullable
        private CollectionInfo remoteAddressBook(long service) {
            @Cleanup Cursor c = db.query(Collections._TABLE, Collections._COLUMNS,
                    Collections.SERVICE_ID + "=? AND " + Collections.SYNC, new String[] { String.valueOf(service) }, null, null, null);
            if (c.moveToNext()) {
                ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(c, values);
                return CollectionInfo.fromDB(values);
            } else
                return null;
        }

    }

}
