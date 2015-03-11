/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Fragment;
import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
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
import at.bitfire.davdroid.resource.LocalCalendar;
import at.bitfire.davdroid.resource.LocalStorageException;
import at.bitfire.davdroid.resource.ServerInfo;
import at.bitfire.davdroid.syncadapter.AccountSettings;

public class AccountDetailsFragment extends Fragment implements TextWatcher {
	public static final String KEY_SERVER_INFO = "server_info";
	
	ServerInfo serverInfo;
	
	EditText editAccountName;
	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.setup_account_details, container, false);
		
		serverInfo = (ServerInfo)getArguments().getSerializable(KEY_SERVER_INFO);
		
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
	
	void addAccount() {
		ServerInfo serverInfo = (ServerInfo)getArguments().getSerializable(KEY_SERVER_INFO);
		String accountName = editAccountName.getText().toString();
		
		AccountManager accountManager = AccountManager.get(getActivity());
		Account account = new Account(accountName, Constants.ACCOUNT_TYPE);
		Bundle userData = AccountSettings.createBundle(serverInfo);
		
		boolean syncContacts = false;
		for (ServerInfo.ResourceInfo addressBook : serverInfo.getAddressBooks())
			if (addressBook.isEnabled()) {
				ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
				syncContacts = true;
				continue;
			}
		if (syncContacts) {
			ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
			ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
		} else
			ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 0);
		
		if (accountManager.addAccountExplicitly(account, serverInfo.getPassword(), userData)) {
			// account created, now create calendars
			boolean syncCalendars = false;
			for (ServerInfo.ResourceInfo calendar : serverInfo.getCalendars())
				if (calendar.isEnabled())
					try {
						LocalCalendar.create(account, getActivity().getContentResolver(), calendar);
						syncCalendars = true;
					} catch (LocalStorageException e) {
						Toast.makeText(getActivity(), "Couldn't create calendar(s): " + e.getMessage(), Toast.LENGTH_LONG).show();
					}
			if (syncCalendars) {
				ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1);
				ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true);
			} else
				ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0);
			
			getActivity().finish();				
		} else
			Toast.makeText(getActivity(), "Couldn't create account (account with this name already existing?)", Toast.LENGTH_LONG).show();
	}

	
	// input validation
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		boolean ok = false;
		ok = editAccountName.getText().length() > 0;
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
