/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Fragment;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.davdroid.resource.LocalCalendar;
import at.bitfire.davdroid.resource.LocalTaskList;
import at.bitfire.davdroid.resource.ServerInfo;
import at.bitfire.davdroid.syncadapter.AccountSettings;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.ical4android.TaskProvider;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;

public class AccountDetailsFragment extends Fragment implements TextWatcher {
	public static final String TAG = "davdroid.AccountDetails";

	ServerInfo serverInfo;
	
	EditText editAccountName;
	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.setup_account_details, container, false);
		
		serverInfo = ((AddAccountActivity)getActivity()).serverInfo;
		
		editAccountName = (EditText)v.findViewById(R.id.account_name);
		editAccountName.addTextChangedListener(this);
		editAccountName.setText(serverInfo.getUserName());
		
		TextView textAccountNameInfo = (TextView)v.findViewById(R.id.account_name_info);
		if (!serverInfo.hasEnabledCalendars())
			textAccountNameInfo.setVisibility(View.GONE);
	
		setHasOptionsMenu(true);
		return v;
	}
	
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.setup_account_details, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.add_account:
			addAccount();
			break;
		default:
			return false;
		}
		return true;
	}


	// actions

    @SuppressWarnings("Recycle")
	void addAccount() {
		String accountName = editAccountName.getText().toString();
		
		AccountManager accountManager = AccountManager.get(getActivity());
		Account account = new Account(accountName, Constants.ACCOUNT_TYPE);
		Bundle userData = AccountSettings.createBundle(serverInfo);

		if (accountManager.addAccountExplicitly(account, serverInfo.getPassword(), userData)) {
			addSync(account, ContactsContract.AUTHORITY, serverInfo.getAddressBooks(), new AddSyncCallback() {
                @Override
                public void createLocalCollection(Account account, ServerInfo.ResourceInfo resource) throws ContactsStorageException {
                    @Cleanup("release") ContentProviderClient provider = getActivity().getContentResolver().acquireContentProviderClient(ContactsContract.AUTHORITY);
                    if (provider != null) {
                        LocalAddressBook addressBook = new LocalAddressBook(account, provider);

                        // set URL
                        addressBook.setURL(resource.getUrl());

                        // set Settings
                        ContentValues settings = new ContentValues(2);
                        settings.put(ContactsContract.Settings.SHOULD_SYNC, 1);
                        settings.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1);
                        addressBook.updateSettings(settings);
                    } else
                        Constants.log.error("Couldn't access Contacts Provider");
                }
            });

			addSync(account, CalendarContract.AUTHORITY, serverInfo.getCalendars(), new AddSyncCallback() {
				@Override
				public void createLocalCollection(Account account, ServerInfo.ResourceInfo calendar) {
                    try {
                        LocalCalendar.create(account, getActivity().getContentResolver(), calendar);
                    } catch(CalendarStorageException e) {
                        Constants.log.error("Couldn't create local calendar", e);
                    }
				}
			});

			addSync(account, TaskProvider.ProviderName.OpenTasks.authority, serverInfo.getTaskLists(), new AddSyncCallback() {
				@Override
				public void createLocalCollection(Account account, ServerInfo.ResourceInfo todoList) {
                    try {
                        LocalTaskList.create(account, getActivity().getContentResolver(), todoList);
                    } catch (CalendarStorageException e) {
                        Constants.log.error("Couldn't create local task list", e);
                    }
                }
			});

			getActivity().finish();				
		} else
			Toast.makeText(getActivity(), "Couldn't create account (account with this name already existing?)", Toast.LENGTH_LONG).show();
	}

	protected interface AddSyncCallback {
		void createLocalCollection(Account account, ServerInfo.ResourceInfo resource) throws ContactsStorageException;
	}

	protected void addSync(Account account, String authority, ServerInfo.ResourceInfo[] resourceList, AddSyncCallback callback) {
		boolean sync = false;
		for (ServerInfo.ResourceInfo resource : resourceList)
			if (resource.isEnabled()) {
				sync = true;
				if (callback != null)
					try {
						callback.createLocalCollection(account, resource);
					} catch(ContactsStorageException e) {
						Log.e(TAG, "Couldn't add sync collection", e);
						Toast.makeText(getActivity(), "Couldn't set up synchronization for " + authority, Toast.LENGTH_LONG).show();
					}
			}
		if (sync) {
			ContentResolver.setIsSyncable(account, authority, 1);
			ContentResolver.setSyncAutomatically(account, authority, true);
		} else
			ContentResolver.setIsSyncable(account, authority, 0);
	}


	// input validation
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		boolean ok = editAccountName.getText().length() > 0;
		MenuItem item = menu.findItem(R.id.add_account);
		item.setEnabled(ok);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		getActivity().invalidateOptionsMenu();
	}

	@Override
	public void afterTextChanged(Editable s) {
	}
}
