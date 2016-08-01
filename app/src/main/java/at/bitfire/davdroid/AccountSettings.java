/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.PeriodicSync;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import at.bitfire.davdroid.model.ServiceDB.HomeSets;
import at.bitfire.davdroid.model.ServiceDB.Services;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.davdroid.resource.LocalCalendar;
import at.bitfire.davdroid.resource.LocalTaskList;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.TaskProvider;
import at.bitfire.vcard4android.ContactsStorageException;
import at.bitfire.vcard4android.GroupMethod;
import lombok.Cleanup;
import okhttp3.HttpUrl;

public class AccountSettings {
    private final static int CURRENT_VERSION = 4;
    private final static String
            KEY_SETTINGS_VERSION = "version",

            KEY_USERNAME = "user_name",
            KEY_AUTH_PREEMPTIVE = "auth_preemptive",

            KEY_WIFI_ONLY = "wifi_only",            // sync on WiFi only (default: false)
            KEY_WIFI_ONLY_SSID = "wifi_only_ssid";  // restrict sync to specific WiFi SSID

    /** Whether to use RFC 6868 for VCards
     *  value = null (not existing)      use RFC6868-style encoding (default value)
     *          "0"                      don't use RFC 6868-style encoding
     */
    private final static String KEY_VCARD_RFC6868 = "vcard_rfc6868";

    /** Time range limitation to the past [in days]
        value = null            default value (DEFAULT_TIME_RANGE_PAST_DAYS)
              < 0 (-1)          no limit
              >= 0              entries more than n days in the past won't be synchronized
     */
    private final static String KEY_TIME_RANGE_PAST_DAYS = "time_range_past_days";
    private final static int DEFAULT_TIME_RANGE_PAST_DAYS = 90;

    /* Whether DAVdroid sets the local calendar color to the value from service DB at every sync
       value = null (not existing)     true (default)
               "0"                     false */
    private final static String KEY_MANAGE_CALENDAR_COLORS = "manage_calendar_colors";

    /** Contact group method:
        value = null (not existing)     groups as separate VCards (default)
                "CATEGORIES"            groups are per-contact CATEGORIES
    */
    private final static String KEY_CONTACT_GROUP_METHOD = "contact_group_method";

    public final static long SYNC_INTERVAL_MANUALLY = -1;

    final Context context;
    final AccountManager accountManager;
    final Account account;


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public AccountSettings(@NonNull Context context, @NonNull Account account) throws InvalidAccountException {
        this.context = context;
        this.account = account;

        accountManager = AccountManager.get(context);

        synchronized(AccountSettings.class) {
            String versionStr = accountManager.getUserData(account, KEY_SETTINGS_VERSION);
            if (versionStr == null)
                throw new InvalidAccountException(account);

            int version = 0;
            try {
                version = Integer.parseInt(versionStr);
            } catch (NumberFormatException ignored) {
            }
            App.log.info("Account " + account.name + " has version " + version + ", current version: " + CURRENT_VERSION);

            if (version < CURRENT_VERSION) {
                Bitmap bitmapLogo = null;
                Drawable drawableLogo = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP ?
                        context.getDrawable(R.drawable.ic_launcher) :
                        context.getResources().getDrawable(R.drawable.ic_launcher);
                if (drawableLogo instanceof BitmapDrawable)
                    bitmapLogo = ((BitmapDrawable)drawableLogo).getBitmap();

                Notification notify = new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.ic_new_releases_light)
                        .setLargeIcon(App.getLauncherBitmap(context))
                        .setContentTitle(context.getString(R.string.settings_version_update))
                        .setContentText(context.getString(R.string.settings_version_update_settings_updated))
                        .setSubText(context.getString(R.string.settings_version_update_install_hint))
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(context.getString(R.string.settings_version_update_settings_updated)))
                        .setCategory(NotificationCompat.CATEGORY_SYSTEM)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(PendingIntent.getActivity(context, 0,
                                new Intent(Intent.ACTION_VIEW, Constants.webUri.buildUpon().appendEncodedPath("faq/entry/davdroid-not-working-after-update/").build()),
                                PendingIntent.FLAG_CANCEL_CURRENT))
                        .setLocalOnly(true)
                        .build();
                NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(Constants.NOTIFICATION_ACCOUNT_SETTINGS_UPDATED, notify);

                update(version);
            }
        }
    }

    public static Bundle initialUserData(String userName, boolean preemptive) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_SETTINGS_VERSION, String.valueOf(CURRENT_VERSION));
        bundle.putString(KEY_USERNAME, userName);
        bundle.putString(KEY_AUTH_PREEMPTIVE, Boolean.toString(preemptive));
        return bundle;
    }


    // authentication settings

    public String username() { return accountManager.getUserData(account, KEY_USERNAME); }
    public void username(@NonNull String userName) { accountManager.setUserData(account, KEY_USERNAME, userName); }

    public String password() { return accountManager.getPassword(account); }
    public void password(@NonNull String password) { accountManager.setPassword(account, password); }

    public boolean preemptiveAuth() { return Boolean.parseBoolean(accountManager.getUserData(account, KEY_AUTH_PREEMPTIVE)); }
    public void preemptiveAuth(boolean preemptive) { accountManager.setUserData(account, KEY_AUTH_PREEMPTIVE, Boolean.toString(preemptive)); }


    // sync. settings

    public Long getSyncInterval(@NonNull String authority) {
        if (ContentResolver.getIsSyncable(account, authority) <= 0)
            return null;

        if (ContentResolver.getSyncAutomatically(account, authority)) {
            List<PeriodicSync> syncs = ContentResolver.getPeriodicSyncs(account, authority);
            if (syncs.isEmpty())
                return SYNC_INTERVAL_MANUALLY;
            else
                return syncs.get(0).period;
        } else
            return SYNC_INTERVAL_MANUALLY;
    }

    public void setSyncInterval(@NonNull String authority, long seconds) {
        if (seconds == SYNC_INTERVAL_MANUALLY) {
            ContentResolver.setSyncAutomatically(account, authority, false);
        } else {
            ContentResolver.setSyncAutomatically(account, authority, true);
            ContentResolver.addPeriodicSync(account, authority, new Bundle(), seconds);
        }
    }

    public boolean getSyncWifiOnly() {
        return accountManager.getUserData(account, KEY_WIFI_ONLY) != null;
    }

    public void setSyncWiFiOnly(boolean wiFiOnly) {
        accountManager.setUserData(account, KEY_WIFI_ONLY, wiFiOnly ? "1" : null);
    }

    @Nullable
    public String getSyncWifiOnlySSID() {
        return accountManager.getUserData(account, KEY_WIFI_ONLY_SSID);
    }

    public void setSyncWifiOnlySSID(String ssid) {
        accountManager.setUserData(account, KEY_WIFI_ONLY_SSID, ssid);
    }


    // CardDAV settings

    public boolean getVCardRFC6868() {
        return accountManager.getUserData(account, KEY_VCARD_RFC6868) == null;
    }

    public void setVCardRFC6868(boolean use) {
        accountManager.setUserData(account, KEY_VCARD_RFC6868, use ? null : "0");
    }


    // CalDAV settings

    @Nullable
    public Integer getTimeRangePastDays() {
        String strDays = accountManager.getUserData(account, KEY_TIME_RANGE_PAST_DAYS);
        if (strDays != null) {
            int days = Integer.valueOf(strDays);
            return days < 0 ? null : days;
        } else
            return DEFAULT_TIME_RANGE_PAST_DAYS;
    }

    public void setTimeRangePastDays(@Nullable Integer days) {
        accountManager.setUserData(account, KEY_TIME_RANGE_PAST_DAYS, String.valueOf(days == null ? -1 : days));
    }

    public boolean getManageCalendarColors() {
        return accountManager.getUserData(account, KEY_MANAGE_CALENDAR_COLORS) == null;
    }

    public void setManageCalendarColors(boolean manage) {
        accountManager.setUserData(account, KEY_MANAGE_CALENDAR_COLORS, manage ? null : "0");
    }


    // CardDAV settings

    @NonNull
    public GroupMethod getGroupMethod() {
        final String name = accountManager.getUserData(account, KEY_CONTACT_GROUP_METHOD);
        return name != null ?
                GroupMethod.valueOf(name) :
                GroupMethod.GROUP_VCARDS;
    }

    public void setGroupMethod(@NonNull GroupMethod method) {
        final String name = method == GroupMethod.GROUP_VCARDS ? null : method.name();
        accountManager.setUserData(account, KEY_CONTACT_GROUP_METHOD, name);
    }


    // update from previous account settings

    private void update(int fromVersion) {
        for (int toVersion = fromVersion + 1; toVersion <= CURRENT_VERSION; toVersion++) {
            App.log.info("Updating account " + account.name + " from version " + fromVersion + " to " + toVersion);
            try {
                Method updateProc = getClass().getDeclaredMethod("update_" + fromVersion + "_" + toVersion);
                updateProc.invoke(this);
                accountManager.setUserData(account, KEY_SETTINGS_VERSION, String.valueOf(toVersion));
            } catch (Exception e) {
                App.log.log(Level.SEVERE, "Couldn't update account settings", e);
            }
            fromVersion = toVersion;
        }
    }

    @SuppressWarnings({ "Recycle", "unused" })
    private void update_1_2() throws ContactsStorageException {
        /* - KEY_ADDRESSBOOK_URL ("addressbook_url"),
           - KEY_ADDRESSBOOK_CTAG ("addressbook_ctag"),
           - KEY_ADDRESSBOOK_VCARD_VERSION ("addressbook_vcard_version") are not used anymore (now stored in ContactsContract.SyncState)
           - KEY_LAST_ANDROID_VERSION ("last_android_version") has been added
        */

        // move previous address book info to ContactsContract.SyncState
        @Cleanup("release") ContentProviderClient provider = context.getContentResolver().acquireContentProviderClient(ContactsContract.AUTHORITY);
        if (provider == null)
            throw new ContactsStorageException("Couldn't access Contacts provider");

        LocalAddressBook addr = new LocalAddressBook(account, provider);

        // until now, ContactsContract.Settings.UNGROUPED_VISIBLE was not set explicitly
        ContentValues values = new ContentValues();
        values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1);
        addr.updateSettings(values);

        String url = accountManager.getUserData(account, "addressbook_url");
        if (!TextUtils.isEmpty(url))
            addr.setURL(url);
        accountManager.setUserData(account, "addressbook_url", null);

        String cTag = accountManager.getUserData(account, "addressbook_ctag");
        if (!TextUtils.isEmpty(cTag))
            addr.setCTag(cTag);
        accountManager.setUserData(account, "addressbook_ctag", null);
    }

    @SuppressWarnings({ "Recycle", "unused" })
    private void update_2_3() {
        // Don't show a warning for Android updates anymore
        accountManager.setUserData(account, "last_android_version", null);

        Long serviceCardDAV = null, serviceCalDAV = null;

        ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(context);
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            // we have to create the WebDAV Service database only from the old address book, calendar and task list URLs

            // CardDAV: migrate address books
            ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(ContactsContract.AUTHORITY);
            if (client != null)
                try {
                    LocalAddressBook addrBook = new LocalAddressBook(account, client);
                    String url = addrBook.getURL();
                    if (url != null) {
                        App.log.fine("Migrating address book " + url);

                        // insert CardDAV service
                        ContentValues values = new ContentValues();
                        values.put(Services.ACCOUNT_NAME, account.name);
                        values.put(Services.SERVICE, Services.SERVICE_CARDDAV);
                        serviceCardDAV = db.insert(Services._TABLE, null, values);

                        // insert address book
                        values.clear();
                        values.put(Collections.SERVICE_ID, serviceCardDAV);
                        values.put(Collections.URL, url);
                        values.put(Collections.SYNC, 1);
                        db.insert(Collections._TABLE, null, values);

                        // insert home set
                        HttpUrl homeSet = HttpUrl.parse(url).resolve("../");
                        values.clear();
                        values.put(HomeSets.SERVICE_ID, serviceCardDAV);
                        values.put(HomeSets.URL, homeSet.toString());
                        db.insert(HomeSets._TABLE, null, values);
                    }

                } catch (ContactsStorageException e) {
                    App.log.log(Level.SEVERE, "Couldn't migrate address book", e);
                } finally {
                    client.release();
                }

            // CalDAV: migrate calendars + task lists
            Set<String> collections = new HashSet<>();
            Set<HttpUrl> homeSets = new HashSet<>();

            client = context.getContentResolver().acquireContentProviderClient(CalendarContract.AUTHORITY);
            if (client != null)
                try {
                    LocalCalendar calendars[] = (LocalCalendar[])LocalCalendar.find(account, client, LocalCalendar.Factory.INSTANCE, null, null);
                    for (LocalCalendar calendar : calendars) {
                        String url = calendar.getName();
                        App.log.fine("Migrating calendar " + url);
                        collections.add(url);
                        homeSets.add(HttpUrl.parse(url).resolve("../"));
                    }
                } catch (CalendarStorageException e) {
                    App.log.log(Level.SEVERE, "Couldn't migrate calendars", e);
                } finally {
                    client.release();
                }

            TaskProvider provider = LocalTaskList.acquireTaskProvider(context.getContentResolver());
            if (provider != null)
                try {
                    LocalTaskList[] taskLists = (LocalTaskList[])LocalTaskList.find(account, provider, LocalTaskList.Factory.INSTANCE, null, null);
                    for (LocalTaskList taskList : taskLists) {
                        String url = taskList.getSyncId();
                        App.log.fine("Migrating task list " + url);
                        collections.add(url);
                        homeSets.add(HttpUrl.parse(url).resolve("../"));
                    }
                } catch (CalendarStorageException e) {
                    App.log.log(Level.SEVERE, "Couldn't migrate task lists", e);
                } finally {
                    provider.close();
                }

            if (!collections.isEmpty()) {
                // insert CalDAV service
                ContentValues values = new ContentValues();
                values.put(Services.ACCOUNT_NAME, account.name);
                values.put(Services.SERVICE, Services.SERVICE_CALDAV);
                serviceCalDAV = db.insert(Services._TABLE, null, values);

                // insert collections
                for (String url : collections) {
                    values.clear();
                    values.put(Collections.SERVICE_ID, serviceCalDAV);
                    values.put(Collections.URL, url);
                    values.put(Collections.SYNC, 1);
                    db.insert(Collections._TABLE, null, values);
                }

                // insert home sets
                for (HttpUrl homeSet : homeSets) {
                    values.clear();
                    values.put(HomeSets.SERVICE_ID, serviceCalDAV);
                    values.put(HomeSets.URL, homeSet.toString());
                    db.insert(HomeSets._TABLE, null, values);
                }
            }
        } finally {
            dbHelper.close();
        }

        // initiate service detection (refresh) to get display names, colors etc.
        Intent refresh = new Intent(context, DavService.class);
        refresh.setAction(DavService.ACTION_REFRESH_COLLECTIONS);
        if (serviceCardDAV != null) {
            refresh.putExtra(DavService.EXTRA_DAV_SERVICE_ID, serviceCardDAV);
            context.startService(refresh);
        }
        if (serviceCalDAV != null) {
            refresh.putExtra(DavService.EXTRA_DAV_SERVICE_ID, serviceCalDAV);
            context.startService(refresh);
        }
    }

    @SuppressWarnings({ "Recycle", "unused" })
    private void update_3_4() {
        setGroupMethod(GroupMethod.CATEGORIES);
    }


    public static class AppUpdatedReceiver extends BroadcastReceiver {

        @Override
        @SuppressLint("UnsafeProtectedBroadcastReceiver")
        public void onReceive(Context context, Intent intent) {
            App.log.info("DAVdroid was updated, checking for AccountSettings version");

            // peek into AccountSettings to initiate a possible migration
            AccountManager accountManager = AccountManager.get(context);
            for (Account account : accountManager.getAccountsByType(Constants.ACCOUNT_TYPE))
                try {
                    App.log.info("Checking account " + account.name);
                    new AccountSettings(context, account);
                } catch (InvalidAccountException e) {
                    App.log.log(Level.SEVERE, "Couldn't check for updated account settings", e);
                }
        }

    }

}
