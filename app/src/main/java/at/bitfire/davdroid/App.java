/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.app.Application;
import android.content.SharedPreferences;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.net.ssl.HostnameVerifier;

import at.bitfire.dav4android.*;
import at.bitfire.davdroid.log.LogcatHandler;
import de.duenndns.ssl.MemorizingTrustManager;
import lombok.Getter;
import okhttp3.internal.tls.OkHostnameVerifier;

public class App extends Application implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String
            PREF_FILE = "global",
            PREF_LOG_TO_FILE = "log_to_file",
            PREF_VERBOSE_LOGGING = "verbose_logging";

    @Getter
    private static SSLSocketFactoryCompat sslSocketFactoryCompat;

    @Getter
    private static HostnameVerifier hostnameVerifier;

    public final static Logger log = Logger.getLogger("davdroid");

    @Getter
    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();

        preferences = getSharedPreferences(PREF_FILE, MODE_PRIVATE);

        // initialize MemorizingTrustManager
        MemorizingTrustManager mtm = new MemorizingTrustManager(this);
        sslSocketFactoryCompat = new SSLSocketFactoryCompat(mtm);
        hostnameVerifier = mtm.wrapHostnameVerifier(OkHostnameVerifier.INSTANCE);

        reinitLogger();
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onTerminate() {
        // will never be called on production devices
        super.onTerminate();

        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        reinitLogger();
    }


    public void reinitLogger() {
        // don't use Android default logging, we have our own handlers
        log.setUseParentHandlers(false);

        // set logging level according to preferences
        log.setLevel(preferences.getBoolean(PREF_VERBOSE_LOGGING, false) ? Level.ALL : Level.INFO);

        // remove all handlers
        for (Handler handler : log.getHandlers())
            log.removeHandler(handler);

        // add logcat handler
        log.addHandler(LogcatHandler.INSTANCE);

        // log to external file according to preferences
        if (preferences.getBoolean(PREF_LOG_TO_FILE, false)) {
            File dir = getExternalFilesDir(null);
            if (dir != null)
                try {
                    String pattern = new File(dir, "davdroid-%u.txt").toString();
                    log.info("Logging to external file: " + pattern);

                    FileHandler fileHandler = new FileHandler(pattern);
                    fileHandler.setFormatter(new SimpleFormatter());
                    log.addHandler(fileHandler);
                } catch (IOException e) {
                    log.log(Level.SEVERE, "Can't create external log file", e);
                }
            else
                log.severe("No external media found, can't create external log file");
        }
    }

}
