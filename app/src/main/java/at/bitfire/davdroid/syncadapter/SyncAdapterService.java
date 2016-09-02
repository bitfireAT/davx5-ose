/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import java.util.logging.Level;

import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.ui.PermissionsActivity;

public abstract class SyncAdapterService extends Service {

    abstract protected AbstractThreadedSyncAdapter syncAdapter();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return syncAdapter().getSyncAdapterBinder();
    }


    public static abstract class SyncAdapter extends AbstractThreadedSyncAdapter {

        public SyncAdapter(Context context) {
            super(context, false);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            App.log.info("Sync for " + authority + " has been initiated");

            // required for dav4android (ServiceLoader)
            Thread.currentThread().setContextClassLoader(getContext().getClassLoader());
        }

        @Override
        public void onSecurityException(Account account, Bundle extras, String authority, SyncResult syncResult) {
            App.log.log(Level.WARNING, "Security exception when opening content provider for " +  authority);
            syncResult.databaseError = true;

            Intent intent = new Intent(getContext(), PermissionsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Notification notify = new NotificationCompat.Builder(getContext())
                    .setSmallIcon(R.drawable.ic_error_light)
                    .setLargeIcon(App.getLauncherBitmap(getContext()))
                    .setContentTitle(getContext().getString(R.string.sync_error_permissions))
                    .setContentText(getContext().getString(R.string.sync_error_permissions_text))
                    .setContentIntent(PendingIntent.getActivity(getContext(), 0, intent, PendingIntent.FLAG_CANCEL_CURRENT))
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .build();
            NotificationManagerCompat nm = NotificationManagerCompat.from(getContext());
            nm.notify(Constants.NOTIFICATION_PERMISSIONS, notify);
        }

        protected boolean checkSyncConditions(@NonNull AccountSettings settings) {
            if (settings.getSyncWifiOnly()) {
                ConnectivityManager cm = (ConnectivityManager)getContext().getSystemService(CONNECTIVITY_SERVICE);
                NetworkInfo network = cm.getActiveNetworkInfo();
                if (network == null) {
                    App.log.info("No network available, stopping");
                    return false;
                }
                if (network.getType() != ConnectivityManager.TYPE_WIFI || !network.isConnected()) {
                    App.log.info("Not on connected WiFi, stopping");
                    return false;
                }

                String onlySSID = settings.getSyncWifiOnlySSID();
                if (onlySSID != null) {
                    onlySSID = "\"" + onlySSID + "\"";
                    WifiManager wifi = (WifiManager)getContext().getSystemService(WIFI_SERVICE);
                    WifiInfo info = wifi.getConnectionInfo();
                    if (info == null || !onlySSID.equals(info.getSSID())) {
                        App.log.info("Connected to wrong WiFi network (" + info.getSSID() + ", required: " + onlySSID + "), ignoring");
                        return false;
                    }
                }
            }
            return true;
        }

    }

}
