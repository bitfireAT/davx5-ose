/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.text.WordUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;

import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;

public class DebugInfoActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<String> {
    public static final String
            KEY_THROWABLE = "throwable",
            KEY_LOGS = "logs",
            KEY_ACCOUNT = "account",
            KEY_AUTHORITY = "authority",
            KEY_PHASE = "phase",
            KEY_LOCAL_RESOURCE = "localResource",
            KEY_REMOTE_RESOURCE = "remoteResource";

    TextView tvReport;
    String report;

    File reportFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_debug_info);
        tvReport = (TextView)findViewById(R.id.text_report);

        getLoaderManager().initLoader(0, getIntent().getExtras(), this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_debug_info, menu);
        return true;
    }


    public void onShare(MenuItem item) {
        if (!TextUtils.isEmpty(report)) {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME + " debug info");

            // since Android 4.1, FileProvider permissions are handled in a useful way (using ClipData)
            boolean asAttachment = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;

            if (asAttachment)
                try {
                    File debugInfoDir = new File(getCacheDir(), "debug-info");
                    debugInfoDir.mkdir();

                    reportFile = new File(debugInfoDir, "debug.txt");
                    App.log.fine("Writing debug info to " + reportFile.getAbsolutePath());
                    FileWriter writer = new FileWriter(reportFile);
                    writer.write(report);
                    writer.close();

                    sendIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, getString(R.string.authority_log_provider), reportFile));
                    sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                } catch(IOException e) {
                    // creating an attachment failed, so send it inline
                    asAttachment = false;

                    StringBuilder builder = new StringBuilder();
                    builder .append("Couldn't write debug info file:\n")
                            .append(ExceptionUtils.getStackTrace(e))
                            .append("\n\n")
                            .append(report);
                    report = builder.toString();
                }

            if (!asAttachment)
                sendIntent.putExtra(Intent.EXTRA_TEXT, report);

            startActivity(Intent.createChooser(sendIntent, null));
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
        @SuppressLint("MissingPermission")
        public String loadInBackground() {
            Throwable throwable = null;
            String  logs = null,
                    authority = null;
            Account account = null;
            int phase = -1;
            String localResource = null,
                   remoteResource = null;

            if (extras != null) {
                throwable = (Throwable)extras.getSerializable(KEY_THROWABLE);
                logs = extras.getString(KEY_LOGS);
                account = extras.getParcelable(KEY_ACCOUNT);
                authority = extras.getString(KEY_AUTHORITY);
                phase = extras.getInt(KEY_PHASE, -1);
                localResource = extras.getString(KEY_LOCAL_RESOURCE);
                remoteResource = extras.getString(KEY_REMOTE_RESOURCE);
            }

            StringBuilder report = new StringBuilder("--- BEGIN DEBUG INFO ---\n");

            // begin with most specific information

            if (phase != -1)
                report.append("SYNCHRONIZATION INFO\nSynchronization phase: ").append(phase).append("\n");
            if (account != null)
                report.append("Account name: ").append(account.name).append("\n");
            if (authority != null)
                report.append("Authority: ").append(authority).append("\n");

            if (throwable instanceof HttpException) {
                HttpException http = (HttpException)throwable;
                if (http.getRequest() != null)
                    report.append("\nHTTP REQUEST:\n").append(http.getRequest()).append("\n\n");
                if (http.getResponse() != null)
                    report.append("HTTP RESPONSE:\n").append(http.getResponse()).append("\n");
            }

            if (localResource != null)
                report.append("\nCURRENT LOCAL RESOURCE:\n")
                      .append(localResource)
                      .append("\n");

            if (remoteResource != null)
                report.append("\nCURRENT REMOTE RESOURCE:\n")
                        .append(remoteResource)
                        .append("\n");

            if (throwable != null)
                report  .append("\nEXCEPTION:\n")
                        .append(ExceptionUtils.getStackTrace(throwable));

            if (logs != null)
                report.append("\nLOGS:\n").append(logs).append("\n");

            final Context context = getContext();

            try {
                PackageManager pm = context.getPackageManager();
                String installedFrom = pm.getInstallerPackageName(BuildConfig.APPLICATION_ID);
                if (TextUtils.isEmpty(installedFrom))
                    installedFrom = "APK (directly)";
                boolean workaroundInstalled = false;
                try {
                    workaroundInstalled = pm.getPackageInfo(BuildConfig.APPLICATION_ID + ".jbworkaround", 0) != null;
                } catch(PackageManager.NameNotFoundException ignored) {}
                report.append("\nSOFTWARE INFORMATION\n" +
                              "Package: ").append(BuildConfig.APPLICATION_ID).append("\n" +
                              "Version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(") ").append(new Date(BuildConfig.buildTime)).append("\n")
                      .append("Installed from: ").append(installedFrom).append("\n")
                      .append("JB Workaround installed: ").append(workaroundInstalled ? "yes" : "no").append("\n\n");
            } catch(Exception ex) {
                App.log.log(Level.SEVERE, "Couldn't get software information", ex);
            }

            report.append("CONFIGURATION\n");
            // power saving
            PowerManager powerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null && Build.VERSION.SDK_INT >= 23)
                report.append("Power saving disabled: ")
                      .append(powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID) ? "yes" : "no")
                      .append("\n");
            // permissions
            for (String permission : new String[] { Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
                                                    Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
                                                    PermissionsActivity.PERMISSION_READ_TASKS, PermissionsActivity.PERMISSION_WRITE_TASKS })
                report.append(permission).append(" permission: ")
                      .append(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED ? "granted" : "denied")
                      .append("\n");
            // system-wide sync settings
            report.append("System-wide synchronization: ")
                  .append(ContentResolver.getMasterSyncAutomatically() ? "automatically" : "manually")
                  .append("\n");
            // main accounts
            AccountManager accountManager = AccountManager.get(context);
            for (Account acct : accountManager.getAccountsByType(context.getString(R.string.account_type)))
                try {
                    AccountSettings settings = new AccountSettings(context, acct);
                    report.append("Account: ").append(acct.name).append("\n" +
                                  "  Address book sync. interval: ").append(syncStatus(settings, context.getString(R.string.address_books_authority))).append("\n" +
                                  "  Calendar     sync. interval: ").append(syncStatus(settings, CalendarContract.AUTHORITY)).append("\n" +
                                  "  OpenTasks    sync. interval: ").append(syncStatus(settings, "org.dmfs.tasks")).append("\n" +
                                  "  WiFi only: ").append(settings.getSyncWifiOnly());
                    if (settings.getSyncWifiOnlySSID() != null)
                        report.append(", SSID: ").append(settings.getSyncWifiOnlySSID());
                    report.append("\n  [CardDAV] Contact group method: ").append(settings.getGroupMethod())
                          .append("\n  [CalDAV] Time range (past days): ").append(settings.getTimeRangePastDays())
                          .append("\n           Manage calendar colors: ").append(settings.getManageCalendarColors())
                          .append("\n");
                } catch(InvalidAccountException e) {
                    report.append(acct).append(" is invalid (unsupported settings version) or does not exist\n");
                }
            // address book accounts
            for (Account acct : accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)))
                try {
                    LocalAddressBook addressBook = new LocalAddressBook(context, acct, null);
                    report.append("Address book account: ").append(acct.name).append("\n" +
                            "  Main account: ").append(addressBook.getMainAccount()).append("\n" +
                            "  URL: ").append(addressBook.getURL()).append("\n" +
                            "  Sync automatically: ").append(ContentResolver.getSyncAutomatically(acct, ContactsContract.AUTHORITY)).append("\n");
                } catch(ContactsStorageException e) {
                    report.append(acct).append(" is invalid: ").append(e.getMessage()).append("\n");
                }
            report.append("\n");

            report.append("SQLITE DUMP\n");
            @Cleanup ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(context);
            dbHelper.dump(report);
            report.append("\n");

            try {
                report.append(
                        "SYSTEM INFORMATION\n" +
                                "Android version: ").append(Build.VERSION.RELEASE).append(" (").append(Build.DISPLAY).append(")\n" +
                                "Device: ").append(WordUtils.capitalize(Build.MANUFACTURER)).append(" ").append(Build.MODEL).append(" (").append(Build.DEVICE).append(")\n\n"
                );
            } catch(Exception ex) {
                App.log.log(Level.SEVERE, "Couldn't get system details", ex);
            }

            report.append("--- END DEBUG INFO ---\n");
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
