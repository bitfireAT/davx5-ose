/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.PeriodicSync;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.ContactsContract;
import android.text.TextUtils;

import org.apache.commons.lang3.math.NumberUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.davdroid.resource.ServerInfo;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;

public class AccountSettings {
	private final static int CURRENT_VERSION = 2;
	private final static String
		KEY_SETTINGS_VERSION = "version",

		KEY_USERNAME = "user_name",
		KEY_AUTH_PREEMPTIVE = "auth_preemptive",
        KEY_LOG_TO_EXTERNAL_FILE = "log_external_file",
        KEY_LOG_VERBOSE = "log_verbose",
        KEY_LAST_ANDROID_VERSION = "last_android_version";

	public final static long SYNC_INTERVAL_MANUALLY = -1;

	final Context context;
	final AccountManager accountManager;
	final Account account;
	
	
    public AccountSettings(Context context, Account account) {
		this.context = context;
		this.account = account;
		
		accountManager = AccountManager.get(context);
		
		synchronized(AccountSettings.class) {
			int version = 0;
			try {
				version = Integer.parseInt(accountManager.getUserData(account, KEY_SETTINGS_VERSION));
			} catch(NumberFormatException e) {
			}
            Constants.log.info("AccountSettings version: v" + version + ", should be: " + version);

			if (version < CURRENT_VERSION) {
                showNotification(Constants.NOTIFICATION_ACCOUNT_SETTINGS_UPDATED,
                        context.getString(R.string.settings_version_update_title),
                        context.getString(R.string.settings_version_update_description));
                update(version);
            }

            // check whether Android version has changed
            String lastAndroidVersionInt = accountManager.getUserData(account, KEY_LAST_ANDROID_VERSION);
            if (lastAndroidVersionInt != null && NumberUtils.toInt(lastAndroidVersionInt) < Build.VERSION.SDK_INT) {
                // notify user
                showNotification(Constants.NOTIFICATION_ANDROID_VERSION_UPDATED,
                        context.getString(R.string.settings_android_update_title),
                        context.getString(R.string.settings_android_update_description));
            }
            accountManager.setUserData(account, KEY_LAST_ANDROID_VERSION, String.valueOf(Build.VERSION.SDK_INT));
		}
	}

    @TargetApi(21)
    protected void showNotification(int id, String title, String message) {
        NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder n = new Notification.Builder(context);
        if (Build.VERSION.SDK_INT >= 16) {
            n.setPriority(Notification.PRIORITY_HIGH);
            n.setStyle(new Notification.BigTextStyle().bigText(message));
        } if (Build.VERSION.SDK_INT >= 20)
            n.setLocalOnly(true);
        if (Build.VERSION.SDK_INT >= 21)
            n.setCategory(Notification.CATEGORY_SYSTEM);
        n.setSmallIcon(R.drawable.ic_launcher);
        n.setContentTitle(title);
        n.setContentText(message);
        nm.notify(id, Build.VERSION.SDK_INT >= 16 ? n.build() : n.getNotification());
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
	public void username(String userName) { accountManager.setUserData(account, KEY_USERNAME, userName); }
	
	public String password() { return accountManager.getPassword(account); }
	public void password(String password) { accountManager.setPassword(account, password); }
	
	public boolean preemptiveAuth() { return Boolean.parseBoolean(accountManager.getUserData(account, KEY_AUTH_PREEMPTIVE)); }
	public void preemptiveAuth(boolean preemptive) { accountManager.setUserData(account, KEY_AUTH_PREEMPTIVE, Boolean.toString(preemptive)); }


    // logging settings

    public boolean logToExternalFile() { return Boolean.parseBoolean(accountManager.getUserData(account, KEY_LOG_TO_EXTERNAL_FILE)); }
    public void logToExternalFile(boolean newValue) { accountManager.setUserData(account, KEY_LOG_TO_EXTERNAL_FILE, Boolean.toString(newValue)); }

    public boolean logVerbose() { return Boolean.parseBoolean(accountManager.getUserData(account, KEY_LOG_VERBOSE)); }
    public void logVerbose(boolean newValue) { accountManager.setUserData(account, KEY_LOG_VERBOSE, Boolean.toString(newValue)); }


	// sync. settings

	public Long getSyncInterval(String authority) {
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

	public void setSyncInterval(String authority, long seconds) {
		if (seconds == SYNC_INTERVAL_MANUALLY) {
			ContentResolver.setSyncAutomatically(account, authority, false);
		} else {
			ContentResolver.setSyncAutomatically(account, authority, true);
			ContentResolver.addPeriodicSync(account, authority, new Bundle(), seconds);
		}
	}

	
	// update from previous account settings
	
	private void update(int fromVersion) {
		for (int toVersion = fromVersion + 1; toVersion <= CURRENT_VERSION; toVersion++)
            updateTo(toVersion);
	}
	
	private void updateTo(int toVersion) {
        final int fromVersion = toVersion - 1;
        Constants.log.info("Updating account settings from v" + fromVersion + " to " + toVersion);
		try {
			switch (toVersion) {
                case 1:
                    update_0_1();
                    break;
                case 2:
                    update_1_2();
                    break;
                default:
                    Constants.log.error("Don't know how to update settings from v" + fromVersion + " to v" + toVersion);
            }
		} catch(Exception e) {
            Constants.log.error("Couldn't update account settings (DAVdroid will probably crash)!", e);
		}
	}

    @SuppressWarnings("Recycle")
	private void update_0_1() throws URISyntaxException {
		String	v0_principalURL = accountManager.getUserData(account, "principal_url"),
				v0_addressBookPath = accountManager.getUserData(account, "addressbook_path");
        Constants.log.debug("Old principal URL = " + v0_principalURL);
        Constants.log.debug("Old address book path = " + v0_addressBookPath);
		
		URI principalURI = new URI(v0_principalURL);

		// update address book
		if (v0_addressBookPath != null) {
			String addressBookURL = principalURI.resolve(v0_addressBookPath).toASCIIString();
            Constants.log.debug("New address book URL = " + addressBookURL);
			accountManager.setUserData(account, "addressbook_url", addressBookURL);
		}
		
		// update calendars
		ContentResolver resolver = context.getContentResolver();
		Uri calendars = Calendars.CONTENT_URI.buildUpon()
				.appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
				.appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
				.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true").build();
		@Cleanup Cursor cursor = resolver.query(calendars, new String[] { Calendars._ID, Calendars.NAME }, null, null, null);
		while (cursor != null && cursor.moveToNext()) {
			int id = cursor.getInt(0);
			String	v0_path = cursor.getString(1),
					v1_url = principalURI.resolve(v0_path).toASCIIString();
            Constants.log.debug("Updating calendar #" + id + " name: " + v0_path + " -> " + v1_url);
			Uri calendar = ContentUris.appendId(Calendars.CONTENT_URI.buildUpon()
					.appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
					.appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
					.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true"), id).build();
			ContentValues newValues = new ContentValues(1);
			newValues.put(Calendars.NAME, v1_url);
			if (resolver.update(calendar, newValues, null, null) != 1)
                Constants.log.debug("Number of modified calendars != 1");
		}

		accountManager.setUserData(account, "principal_url", null);
		accountManager.setUserData(account, "addressbook_path", null);

		accountManager.setUserData(account, KEY_SETTINGS_VERSION, "1");
	}

    @SuppressWarnings("Recycle")
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

        accountManager.setUserData(account, KEY_SETTINGS_VERSION, "2");
    }

}
