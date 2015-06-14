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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.PeriodicSync;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.util.Log;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import at.bitfire.davdroid.resource.ServerInfo;
import ezvcard.VCardVersion;
import lombok.Cleanup;

public class AccountSettings {
	private final static String TAG = "davdroid.AccountSettings";
	
	private final static int CURRENT_VERSION = 1;
	private final static String
		KEY_SETTINGS_VERSION = "version",
		
		KEY_USERNAME = "user_name",
		KEY_AUTH_PREEMPTIVE = "auth_preemptive",
		
		KEY_ADDRESSBOOK_URL = "addressbook_url",
		KEY_ADDRESSBOOK_CTAG = "addressbook_ctag",
		KEY_ADDRESSBOOK_VCARD_VERSION = "addressbook_vcard_version";

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
			if (version < CURRENT_VERSION)
				update(version);
		}
	}
	
	
	public static Bundle createBundle(ServerInfo serverInfo) {
		Bundle bundle = new Bundle();
		bundle.putString(KEY_SETTINGS_VERSION, String.valueOf(CURRENT_VERSION));
		bundle.putString(KEY_USERNAME, serverInfo.getUserName());
		bundle.putString(KEY_AUTH_PREEMPTIVE, Boolean.toString(serverInfo.isAuthPreemptive()));
		for (ServerInfo.ResourceInfo addressBook : serverInfo.getAddressBooks())
			if (addressBook.isEnabled()) {
				bundle.putString(KEY_ADDRESSBOOK_URL, addressBook.getURL());
				bundle.putString(KEY_ADDRESSBOOK_VCARD_VERSION, addressBook.getVCardVersion().getVersion());
				break;
			}
		return bundle;
	}
	
	
	// authentication settings

	public String getUserName() {
		return accountManager.getUserData(account, KEY_USERNAME);		
	}
	public void setUserName(String userName) { accountManager.setUserData(account, KEY_USERNAME, userName); }
	
	public String getPassword() {
		return accountManager.getPassword(account);
	}
	public void setPassword(String password) { accountManager.setPassword(account, password); }
	
	public boolean getPreemptiveAuth() { return Boolean.parseBoolean(accountManager.getUserData(account, KEY_AUTH_PREEMPTIVE)); }
	public void setPreemptiveAuth(boolean preemptive) { accountManager.setUserData(account, KEY_AUTH_PREEMPTIVE, Boolean.toString(preemptive)); }


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


	// address book (CardDAV) settings
	
	public String getAddressBookURL() {
		return accountManager.getUserData(account, KEY_ADDRESSBOOK_URL);
	}
	
	public String getAddressBookCTag() {
		return accountManager.getUserData(account, KEY_ADDRESSBOOK_CTAG);
	}
	
	public void setAddressBookCTag(String cTag) {
		accountManager.setUserData(account, KEY_ADDRESSBOOK_CTAG, cTag);
	}
	
	public VCardVersion getAddressBookVCardVersion() {
		VCardVersion version = VCardVersion.V3_0;
		String versionStr = accountManager.getUserData(account, KEY_ADDRESSBOOK_VCARD_VERSION);
		if (versionStr != null)
			version = VCardVersion.valueOfByStr(versionStr);
		return version;
	}
	
	
	// update from previous account settings
	
	private void update(int fromVersion) {
		Log.i(TAG, "Account settings must be updated from v" + fromVersion + " to v" + CURRENT_VERSION);
		for (int toVersion = CURRENT_VERSION; toVersion > fromVersion; toVersion--)
			update(fromVersion, toVersion);
	}
	
	private void update(int fromVersion, int toVersion) {
		Log.i(TAG, "Updating account settings from v" + fromVersion + " to " + toVersion);
		try {
			if (fromVersion == 0 && toVersion == 1)
				update_0_1();
			else
				Log.wtf(TAG, "Don't know how to update settings from v" + fromVersion + " to v" + toVersion);
		} catch(Exception e) {
			Log.e(TAG, "Couldn't update account settings (DAVdroid will probably crash)!", e);
		}
	}
	
	private void update_0_1() throws URISyntaxException {
		String	v0_principalURL = accountManager.getUserData(account, "principal_url"),
				v0_addressBookPath = accountManager.getUserData(account, "addressbook_path");
		Log.d(TAG, "Old principal URL = " + v0_principalURL);
		Log.d(TAG, "Old address book path = " + v0_addressBookPath);
		
		URI principalURI = new URI(v0_principalURL);

		// update address book
		if (v0_addressBookPath != null) {
			String addressBookURL = principalURI.resolve(v0_addressBookPath).toASCIIString();
			Log.d(TAG, "New address book URL = " + addressBookURL);
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
			Log.d(TAG, "Updating calendar #" + id + " name: " + v0_path + " -> " + v1_url);
			Uri calendar = ContentUris.appendId(Calendars.CONTENT_URI.buildUpon()
					.appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
					.appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
					.appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true"), id).build();
			ContentValues newValues = new ContentValues(1);
			newValues.put(Calendars.NAME, v1_url);
			if (resolver.update(calendar, newValues, null, null) != 1)
				Log.e(TAG, "Number of modified calendars != 1");
		}
		
		Log.d(TAG, "Cleaning old principal URL and address book path");
		accountManager.setUserData(account, "principal_url", null);
		accountManager.setUserData(account, "addressbook_path", null);
		
		Log.d(TAG, "Updated settings successfully!");
		accountManager.setUserData(account, KEY_SETTINGS_VERSION, "1");
	}
}
