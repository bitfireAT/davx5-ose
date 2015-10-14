/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.io.PrintWriter;
import java.io.StringWriter;

import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import lombok.Cleanup;

public class DebugInfoActivity extends Activity {
    public static final String
            KEY_EXCEPTION = "exception",
            KEY_ACCOUNT = "account",
            KEY_PHASE = "phase";

    private static final String APP_ID = "at.bitfire.davdroid";

    String report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.debug_info_activity);

        TextView tvReport = (TextView)findViewById(R.id.text_report);
        tvReport.setText(generateReport(getIntent().getExtras()));
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


    protected String generateReport(Bundle extras) {
        Exception exception = null;
        Account account = null;
        Integer phase = null;

        if (extras != null) {
            exception = (Exception) extras.getSerializable(KEY_EXCEPTION);
            account = (Account) extras.getParcelable(KEY_ACCOUNT);
            phase = extras.getInt(KEY_PHASE);
        }

        StringBuilder report = new StringBuilder();

        try {
            report.append(
                    "SYSTEM INFORMATION\n" +
                            "Android version: " + Build.VERSION.RELEASE + " (" + Build.DISPLAY + ")\n" +
                            "Device: " + Build.MANUFACTURER + " / " + Build.MODEL + " (" + Build.DEVICE + ")\n\n"
            );
        } catch (Exception ex) {
            Constants.log.error("Couldn't get system details", ex);
        }

        try {
            PackageManager pm = getPackageManager();
            String installedFrom = pm.getInstallerPackageName("at.bitfire.davdroid");
            if (TextUtils.isEmpty(installedFrom))
                installedFrom = "APK (directly)";
            else {
                PackageInfo installer = pm.getPackageInfo(installedFrom, PackageManager.GET_META_DATA);
                if (installer != null)
                    installedFrom = pm.getApplicationLabel(installer.applicationInfo).toString();
            }
            report.append(
                    "SOFTWARE INFORMATION\n" +
                    "DAVdroid version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") " + BuildConfig.buildTime.toString() + "\n" +
                    "Installed from: " + installedFrom + "\n\n"
            );
        } catch(Exception ex) {
            Constants.log.error("Couldn't get software information", ex);
        }

        report.append(
                "CONFIGURATION\n" +
                "System-wide  synchronization: " + (ContentResolver.getMasterSyncAutomatically() ? "automatically" : "manually") + " (overrides account settings)\n"
        );
        if (account != null)
            report.append(
                    "Account name: " + account.name + "\n" +
                    "Address book synchronization: " + syncStatus(account, ContactsContract.AUTHORITY) + "\n" +
                    "Calendar     synchronization: " + syncStatus(account, CalendarContract.AUTHORITY) + "\n" +
                    "OpenTasks    synchronization: " + syncStatus(account, "org.dmfs.tasks") + "\n\n"
            );

        if (phase != null) {
            report.append("SYNCHRONIZATION INFO\nSychronization phase: " + phase + "\n\n");
        }

        if (exception instanceof HttpException) {
            HttpException http = (HttpException)exception;
            if (http.request != null)
                report.append("HTTP REQUEST:\n" + http.request + "\n\n");
            if (http.response != null)
            report.append("HTTP RESPONSE:\n" + http.response + "\n\n");
        }

        if (exception != null) {
            report.append("STACK TRACE\n");
            StringWriter writer = new StringWriter();
            @Cleanup PrintWriter printWriter = new PrintWriter(writer);
            exception.printStackTrace(printWriter);
            report.append(writer.toString());
        }

        return report.toString();
    }

    protected static String syncStatus(Account account, String authority) {
        return ContentResolver.getIsSyncable(account, authority) > 0 ?
                (ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY) ? "automatically" : "manually") :
                "—";
    }

}
