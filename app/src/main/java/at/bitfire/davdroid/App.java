/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.accounts.Account;
import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import org.apache.commons.lang3.time.DateFormatUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;

import at.bitfire.davdroid.log.LogcatHandler;
import at.bitfire.davdroid.log.PlainTextFormatter;
import de.duenndns.ssl.MemorizingTrustManager;
import lombok.Getter;
import okhttp3.internal.tls.OkHostnameVerifier;

public class App extends Application implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String
            PREF_FILE = "davdroid_preferences",             // preference file name
            PREF_LOG_TO_FILE = "log_to_file";               // boolean: external logging enabled

    @Getter
    private static MemorizingTrustManager memorizingTrustManager;

    @Getter
    private static SSLSocketFactoryCompat sslSocketFactoryCompat;

    @Getter
    private static HostnameVerifier hostnameVerifier;

    public final static Logger log = Logger.getLogger("davdroid");

    @Getter
    private static SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();

        preferences = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(this);

        // initialize MemorizingTrustManager
        memorizingTrustManager = new MemorizingTrustManager(this);
        sslSocketFactoryCompat = new SSLSocketFactoryCompat(memorizingTrustManager);
        hostnameVerifier = memorizingTrustManager.wrapHostnameVerifier(OkHostnameVerifier.INSTANCE);

        // initializer logger
        reinitLogger();
    }

    // won't be called in production
    @Override
    public void onTerminate() {
        super.onTerminate();
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_LOG_TO_FILE.equals(key)) {
            log.info("Logging preferences changed, initializing logger again");
            reinitLogger();
        }
    }

    private void reinitLogger() {
        // don't use Android default logging, we have our own handlers
        log.setUseParentHandlers(false);

        boolean logToFile = preferences.getBoolean(PREF_LOG_TO_FILE, false),
                logVerbose = logToFile || Log.isLoggable(log.getName(), Log.DEBUG);

        // set logging level according to preferences
        log.setLevel(logVerbose ? Level.ALL : Level.INFO);

        // remove all handlers
        for (Handler handler : log.getHandlers())
            log.removeHandler(handler);

        // add logcat handler
        log.addHandler(LogcatHandler.INSTANCE);

        // log to external file according to preferences
        if (logToFile) {
            File dir = getExternalFilesDir(null);
            if (dir != null)
                try {
                    String pattern = new File(dir, "davdroid%u-" + DateFormatUtils.format(System.currentTimeMillis(), "yyyyMMdd-HHmmss") + ".txt").toString();
                    log.info("Logging to external file: " + pattern);

                    FileHandler fileHandler = new FileHandler(pattern);
                    fileHandler.setFormatter(PlainTextFormatter.DEFAULT);
                    log.addHandler(fileHandler);
                } catch (IOException e) {
                    log.log(Level.SEVERE, "Can't create external log file", e);
                }
            else
                log.severe("No external media found, can't create external log file");
        }
    }

}
