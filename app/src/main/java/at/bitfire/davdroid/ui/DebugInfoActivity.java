/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.text.WordUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.syncadapter.AccountSettings;

public class DebugInfoActivity extends Activity implements LoaderManager.LoaderCallbacks<String> {
    public static final String
            KEY_EXCEPTION = "exception",
            KEY_LOGS = "logs",
            KEY_ACCOUNT = "account",
            KEY_AUTHORITY = "authority",
            KEY_PHASE = "phase";

    private static final int MAX_INLINE_REPORT_LENGTH = 8000;

    TextView tvReport;
    String report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.debug_info_activity);
        tvReport = (TextView)findViewById(R.id.text_report);

        getLoaderManager().initLoader(0, getIntent().getExtras(), this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.exception_details_activity, menu);
        return true;
    }


    public void onShare(MenuItem item) {
        if (!TextUtils.isEmpty(report)) {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "DAVdroid debug info");

            boolean inline = false;

            if (report.length() > MAX_INLINE_REPORT_LENGTH)
                // report is too long for inline text, send it as an attachment
                try {
                    File reportFile = File.createTempFile("davdroid-debug", ".txt", getExternalCacheDir());
                    Constants.log.debug("Writing debug info to " + reportFile.getAbsolutePath());
                    FileWriter writer = new FileWriter(reportFile);
                    writer.write(report);
                    writer.close();

                    sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(reportFile));
                } catch (IOException e) {
                    // let's hope the report is < 1 MB (Android IPC limit)
                    inline = true;
                }
            else
                inline = true;

            if (inline)
                sendIntent.putExtra(Intent.EXTRA_TEXT, report);

            startActivity(sendIntent);
        }
    }


    @Override
    public Loader<String> onCreateLoader(int id, Bundle args) {
        return new ReportLoader(this, args);
    }

    @Override
    public void onLoadFinished(Loader<String> loader, String data) {
        if (data != null)
            tvReport.setText(report = data);
    }

    @Override
    public void onLoaderReset(Loader<String> loader) {
    }


    static class ReportLoader extends AsyncTaskLoader<String> {

        final Bundle extras;

        public ReportLoader(Context context, Bundle extras) {
            super(context);
            this.extras = extras;
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public String loadInBackground() {
            Exception exception = null;
            String  logs = null,
                    authority = null;
            Account account = null;
            int phase = -1;

            if (extras != null) {
                exception = (Exception)extras.getSerializable(KEY_EXCEPTION);
                logs = extras.getString(KEY_LOGS);
                account = extras.getParcelable(KEY_ACCOUNT);
                authority = extras.getString(KEY_AUTHORITY);
                phase = extras.getInt(KEY_PHASE, -1);
            }

            StringBuilder report = new StringBuilder();

            // begin with most specific information

            if (phase != -1)
                report.append("SYNCHRONIZATION INFO\nSynchronization phase: " + phase + "\n");
            if (account != null)
                report.append("Account name: " + account.name + "\n");
            if (authority != null)
                report.append("Authority: " + authority + "\n\n");

            if (exception instanceof HttpException) {
                HttpException http = (HttpException)exception;
                if (http.request != null)
                    report.append("HTTP REQUEST:\n" + http.request + "\n\n");
                if (http.response != null)
                    report.append("HTTP RESPONSE:\n" + http.response + "\n\n");
            }

            if (exception != null) {
                report.append("STACK TRACE:\n");
                for (String stackTrace : ExceptionUtils.getRootCauseStackTrace(exception))
                    report.append(stackTrace + "\n");
                report.append("\n");
            }

            if (logs != null)
                report.append("LOGS:\n" + logs + "\n");

            try {
                PackageManager pm = getContext().getPackageManager();
                String installedFrom = pm.getInstallerPackageName(BuildConfig.APPLICATION_ID);
                if (TextUtils.isEmpty(installedFrom))
                    installedFrom = "APK (directly)";
                boolean workaroundInstalled = false;
                try {
                    workaroundInstalled = pm.getPackageInfo("at.bitfire.davdroid.jbworkaround", 0) != null;
                } catch(PackageManager.NameNotFoundException e) {}
                report.append(
                        "SOFTWARE INFORMATION\n" +
                                "DAVdroid version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") " + BuildConfig.buildTime.toString() + "\n" +
                                "Installed from: " + installedFrom + "\n" +
                                "JB Workaround installed: " + (workaroundInstalled ? "yes" : "no") + "\n\n"
                );
            } catch(Exception ex) {
                Constants.log.error("Couldn't get software information", ex);
            }

            report.append(
                    "CONFIGURATION\n" +
                            "System-wide synchronization: " + (ContentResolver.getMasterSyncAutomatically() ? "automatically" : "manually") + "\n"
            );
            AccountManager accountManager = AccountManager.get(getContext());
            for (Account acct : accountManager.getAccountsByType(Constants.ACCOUNT_TYPE)) {
                AccountSettings settings = new AccountSettings(getContext(), acct);
                report.append(
                        "Account: " + acct.name + "\n" +
                                "  Address book sync. interval: " + syncStatus(settings, ContactsContract.AUTHORITY) + "\n" +
                                "  Calendar     sync. interval: " + syncStatus(settings, CalendarContract.AUTHORITY) + "\n" +
                                "  OpenTasks    sync. interval: " + syncStatus(settings, "org.dmfs.tasks") + "\n"
                        );
            }
            report.append("\n");

            try {
                report.append(
                        "SYSTEM INFORMATION\n" +
                                "Android version: " + Build.VERSION.RELEASE + " (" + Build.DISPLAY + ")\n" +
                                "Device: " + WordUtils.capitalize(Build.MANUFACTURER) + " " + Build.MODEL + " (" + Build.DEVICE + ")\n\n"
                );
            } catch (Exception ex) {
                Constants.log.error("Couldn't get system details", ex);
            }

            return report.toString();
        }

        protected String syncStatus(AccountSettings settings, String authority) {
            Long interval = settings.getSyncInterval(authority);
            return interval != null ?
                    (interval == AccountSettings.SYNC_INTERVAL_MANUALLY ? "manually" : interval/60 + " min") :
                    "—";
        }
    }

}
