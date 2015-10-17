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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.apache.commons.lang3.exception.ExceptionUtils;

import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;

public class DebugInfoActivity extends Activity implements LoaderManager.LoaderCallbacks<String> {
    public static final String
            KEY_EXCEPTION = "exception",
            KEY_ACCOUNT = "account",
            KEY_AUTHORITY = "authority",
            KEY_PHASE = "phase";

    TextView tvReport;
    String report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.debug_info_activity);

        tvReport = (TextView)findViewById(R.id.text_report);
        //tvReport.setText(report = generateReport(getIntent().getExtras()));

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
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "DAVdroid Exception Details");
            sendIntent.putExtra(Intent.EXTRA_TEXT, report);
            sendIntent.setType("text/plain");
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
            String authority = null;
            Account account = null;
            Integer phase = null;

            if (extras != null) {
                exception = (Exception)extras.getSerializable(KEY_EXCEPTION);
                account = extras.getParcelable(KEY_ACCOUNT);
                authority = extras.getString(KEY_AUTHORITY);
                phase = extras.getInt(KEY_PHASE);
            }

            StringBuilder report = new StringBuilder();

            // begin with most specific information

            if (phase != null)
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
                    report.append("HTTP RESPONSE:\n" + http.response + "\n");
            }

            if (exception != null) {
                report.append("STACK TRACE:\n");
                for (String stackTrace : ExceptionUtils.getRootCauseStackTrace(exception))
                    report.append(stackTrace + "\n");
                report.append("\n");
            }

            try {
                PackageManager pm = getContext().getPackageManager();
                String installedFrom = pm.getInstallerPackageName(BuildConfig.APPLICATION_ID);
                if (TextUtils.isEmpty(installedFrom))
                    installedFrom = "APK (directly)";
                else {
                    PackageInfo installer = pm.getPackageInfo(installedFrom, PackageManager.GET_META_DATA);
                    if (installer != null)
                        installedFrom = pm.getApplicationLabel(installer.applicationInfo).toString();
                }
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
            for (Account acc : accountManager.getAccountsByType(Constants.ACCOUNT_TYPE)) {
                report.append(
                        "  Account: " + acc.name + "\n" +
                                "    Address book synchronization: " + syncStatus(acc, ContactsContract.AUTHORITY) + "\n" +
                                "    Calendar     synchronization: " + syncStatus(acc, CalendarContract.AUTHORITY) + "\n" +
                                "    OpenTasks    synchronization: " + syncStatus(acc, "org.dmfs.tasks") + "\n\n"
                );
            }

            try {
                report.append(
                        "SYSTEM INFORMATION\n" +
                                "Android version: " + Build.VERSION.RELEASE + " (" + Build.DISPLAY + ")\n" +
                                "Device: " + Build.MANUFACTURER + " / " + Build.MODEL + " (" + Build.DEVICE + ")\n\n"
                );
            } catch (Exception ex) {
                Constants.log.error("Couldn't get system details", ex);
            }

            return report.toString();
        }

        protected String syncStatus(Account account, String authority) {
            return ContentResolver.getIsSyncable(account, authority) > 0 ?
                    (ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY) ? "automatically" : "manually") :
                    "—";
        }
    }

}
