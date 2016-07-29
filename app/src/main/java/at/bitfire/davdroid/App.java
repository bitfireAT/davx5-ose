/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.app.Application;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import org.apache.commons.lang3.time.DateFormatUtils;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;

import at.bitfire.davdroid.log.LogcatHandler;
import at.bitfire.davdroid.log.PlainTextFormatter;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.Settings;
import de.duenndns.ssl.MemorizingTrustManager;
import lombok.Cleanup;
import lombok.Getter;
import okhttp3.internal.tls.OkHostnameVerifier;

public class App extends Application {
    public static final String
            FLAVOR_GOOGLE_PLAY = "gplay",
            FLAVOR_ICLOUD = "icloud",
            FLAVOR_STANDARD = "standard";

    public static final String LOG_TO_EXTERNAL_STORAGE = "logToExternalStorage";

    @Getter
    private static MemorizingTrustManager memorizingTrustManager;

    @Getter
    private static SSLSocketFactoryCompat sslSocketFactoryCompat;

    @Getter
    private static HostnameVerifier hostnameVerifier;

    public final static Logger log = Logger.getLogger("davdroid");
    static {
        at.bitfire.dav4android.Constants.log = Logger.getLogger("davdroid.dav4android");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // initialize MemorizingTrustManager
        if (BuildConfig.useMTM) {
            memorizingTrustManager = new MemorizingTrustManager(this);
            sslSocketFactoryCompat = new SSLSocketFactoryCompat(memorizingTrustManager);
            hostnameVerifier = memorizingTrustManager.wrapHostnameVerifier(OkHostnameVerifier.INSTANCE);
        }

        // initializer logger
        reinitLogger();
    }

    public void reinitLogger() {
        // don't use Android default logging, we have our own handlers
        log.setUseParentHandlers(false);

        @Cleanup ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(this);
        Settings settings = new Settings(dbHelper.getReadableDatabase());

        boolean logToFile = settings.getBoolean(LOG_TO_EXTERNAL_STORAGE, false),
                logVerbose = logToFile || Log.isLoggable(log.getName(), Log.DEBUG);

        // set logging level according to preferences
        log.setLevel(logVerbose ? Level.ALL : Level.INFO);

        // remove all handlers
        for (Handler handler : log.getHandlers())
            log.removeHandler(handler);

        // add logcat handler
        log.addHandler(LogcatHandler.INSTANCE);

        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        // log to external file according to preferences
        if (logToFile) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
            builder .setSmallIcon(R.drawable.ic_sd_storage_light)
                    .setLargeIcon(((BitmapDrawable)getResources().getDrawable(R.drawable.ic_logo)).getBitmap())
                    .setContentTitle(getString(R.string.logging_davdroid_file_logging))
                    .setLocalOnly(true);

            File dir = getExternalFilesDir(null);
            if (dir != null)
                try {
                    String fileName = new File(dir, "davdroid-" + android.os.Process.myPid() + "-" +
                            DateFormatUtils.format(System.currentTimeMillis(), "yyyyMMdd-HHmmss") + ".txt").toString();
                    log.info("Logging to " + fileName);

                    FileHandler fileHandler = new FileHandler(fileName);
                    fileHandler.setFormatter(PlainTextFormatter.DEFAULT);
                    log.addHandler(fileHandler);
                    builder .setContentText(dir.getPath())
                            .setSubText(getString(R.string.logging_to_external_storage_warning))
                            .setCategory(NotificationCompat.CATEGORY_STATUS)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText(getString(R.string.logging_to_external_storage, dir.getPath())))
                            .setOngoing(true);

                } catch (IOException e) {
                    log.log(Level.SEVERE, "Couldn't create external log file", e);

                    builder .setContentText(getString(R.string.logging_couldnt_create_file, e.getLocalizedMessage()))
                            .setCategory(NotificationCompat.CATEGORY_ERROR);
                }
            else
                builder.setContentText(getString(R.string.logging_no_external_storage));

            nm.notify(Constants.NOTIFICATION_EXTERNAL_FILE_LOGGING, builder.build());
        } else
            nm.cancel(Constants.NOTIFICATION_EXTERNAL_FILE_LOGGING);
    }


    public static class ReinitLoggingReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            log.info("Received broadcast: re-initializing logger");

            App app = (App)context.getApplicationContext();
            app.reinitLogger();
        }

    }
}
