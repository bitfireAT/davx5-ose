/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import lombok.Cleanup;

public class ContactsSyncAdapterService extends SyncAdapterService {

    @Override
    protected AbstractThreadedSyncAdapter syncAdapter() {
        return new ContactsSyncAdapter(this);
    }


	private static class ContactsSyncAdapter extends SyncAdapter {

        public ContactsSyncAdapter(Context context) {
            super(context);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            super.onPerformSync(account, extras, authority, provider, syncResult);

            SQLiteOpenHelper dbHelper = new ServiceDB.OpenHelper(getContext());
            try {
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                Long service = getService(db, account);
                if (service != null) {
                    CollectionInfo remote = remoteAddressBook(db, service);
                    if (remote != null)
                        try {
                            ContactsSyncManager syncManager = new ContactsSyncManager(getContext(), account, extras, authority, provider, syncResult, remote);
                            syncManager.performSync();
                        } catch(InvalidAccountException e) {
                            App.log.log(Level.SEVERE, "Couldn't get account settings", e);
                        }
                    else
                        App.log.info("No address book collection selected for synchronization");
                } else
                    App.log.info("No CardDAV service found in DB");
            } finally {
                dbHelper.close();
            }

            App.log.info("Address book sync complete");
        }

        @Nullable
        private Long getService(@NonNull SQLiteDatabase db, @NonNull Account account) {
            @Cleanup Cursor c = db.query(ServiceDB.Services._TABLE, new String[] { ServiceDB.Services.ID },
                    ServiceDB.Services.ACCOUNT_NAME + "=? AND " + ServiceDB.Services.SERVICE + "=?", new String[] { account.name, ServiceDB.Services.SERVICE_CARDDAV }, null, null, null);
            if (c.moveToNext())
                return c.getLong(0);
            else
                return null;
        }

        @Nullable
        private CollectionInfo remoteAddressBook(@NonNull SQLiteDatabase db, long service) {
            @Cleanup Cursor c = db.query(Collections._TABLE, null,
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
