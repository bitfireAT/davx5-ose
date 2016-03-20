/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
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
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.model.ServiceDB;

public abstract class SyncAdapterService extends Service {

    ServiceDB.OpenHelper dbHelper;
    AbstractThreadedSyncAdapter syncAdapter;

    @Override
    public void onCreate() {
        dbHelper = new ServiceDB.OpenHelper(this);
    }

    @Override
    public void onDestroy() {
        dbHelper.close();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return syncAdapter.getSyncAdapterBinder();
    }


    public static abstract class SyncAdapter extends AbstractThreadedSyncAdapter {
        protected final SQLiteDatabase db;

        public SyncAdapter(Context context, ServiceDB.OpenHelper dbHelper) {
            super(context, false);
            db = dbHelper.getReadableDatabase();
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            App.log.info("Starting " + authority + " sync");

            // required for dav4android (ServiceLoader)
            Thread.currentThread().setContextClassLoader(getContext().getClassLoader());

            // peek into AccountSettings to cause possible migration (v0.9 -> v1.0)
            new AccountSettings(getContext(), account);
        }
    }

}
