package at.bitfire.davdroid.syncadapter;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;

import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.ServerManagedPolicy;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;

public class LicenseCheckSyncPlugin implements ISyncPlugin, LicenseCheckerCallback {

    private static final String LICENSE_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA046s5AzGwaRpSYu4FJ5bTkRvIc93P8dhVXDhgAD7M946R8PE1zKCLPSep4eupw8nxQThXOK1OOJUeh8UbtBBlK+V51UNCjFXQcrLT9XmVmd2OpqhUHszhp3ESg6XpqRwKM7cX4uccpFWKRnn+epTzWoUMTlzaqGQWfF+YSCGMnkJzttdXWzYswhHebQPKYwnaTlGGc0sGl+K6XV/ewe/G47K5sKpX1qdFSBmloH79jFlOumpGooHn51YQ0mTTNu1ErNHi0iugYTDN9QNpLVEtWmxFLwDgwzgXOgd2X2UQDMPCgWapatQWfzUWcIjGLqprcPn29VW3ckQyIx0YMaEzQIDAQAB";
    private static final byte OBFUSCATOR_SALT[] = { 72, 52, 61, -90, -65, 114, 99, 77, -54, 56, 44, 124, 57, 45, -91, 67, -88, 35, -102, 21 };

    private LicenseChecker licenseChecker;

    private static Boolean licenseOk;
    private static final Object licenseLock = new Object();
    private static final int TIMEOUT = 30000;


    @Override
    public synchronized boolean beforeSync(@NonNull Context context, @NonNull SyncResult syncResult) {
        synchronized(licenseLock) {
            if (licenseOk == null || !licenseOk) {
                App.log.info("Checking Google Play license");
                licenseOk = null;

                @SuppressLint("HardwareIds") final String deviceId = Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                licenseChecker = new LicenseChecker(context, new ServerManagedPolicy(context, new AESObfuscator(OBFUSCATOR_SALT, BuildConfig.APPLICATION_ID, deviceId)), LICENSE_PUBLIC_KEY);
                licenseChecker.checkAccess(this);
                if (licenseOk == null)
                    try {
                        licenseLock.wait(TIMEOUT);
                    } catch(InterruptedException ignored) {
                    }
            }

            if (licenseOk == null)
                licenseOk = false;

            if (licenseChecker != null) {
                licenseChecker.onDestroy();
                licenseChecker = null;
            }

            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            if (!licenseOk) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID));
                intent.setPackage("com.android.vending");   // open only with Play Store

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
                builder.setSmallIcon(R.drawable.ic_error_light)
                        .setLargeIcon(App.getLauncherBitmap(context))
                        .setContentTitle("License check failed")
                        .setContentText("Couldn't verify Google Play purchase")
                        .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                        .setCategory(NotificationCompat.CATEGORY_ERROR)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);
                nm.notify(Constants.NOTIFICATION_SUBSCRIPTION, builder.build());

                App.log.warning("No valid license, skipping sync");
                syncResult.stats.numIoExceptions++;
                syncResult.delayUntil = 15;     // wait 15 seconds before trying again
            } else
                nm.cancel(Constants.NOTIFICATION_SUBSCRIPTION);

            return licenseOk;
        }
    }

    @Override
    public void afterSync(@NonNull Context context, @NonNull SyncResult syncResult) {
    }


    @Override
    public void allow(int reason) {
        App.log.info("Google Play license valid: " + reason);
        synchronized(licenseLock) {
            licenseOk = true;
            licenseLock.notify();
        }
    }

    @Override
    public void dontAllow(int reason) {
        App.log.warning("Google Play license invalid: " + reason);
        synchronized(licenseLock) {
            licenseOk = false;
            licenseLock.notify();
        }
    }

    @Override
    public void applicationError(int errorCode) {
        App.log.severe("Application error " + errorCode + " when checking Google Play license");
        synchronized(licenseLock) {
            licenseOk = false;
            licenseLock.notify();
        }
    }

}
