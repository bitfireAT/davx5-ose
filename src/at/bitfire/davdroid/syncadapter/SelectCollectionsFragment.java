/*******************************************************************************
 * Copyright (c) 2013 Richard Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ListFragment;
import android.content.ContentResolver;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.resource.LocalCalendar;

public class SelectCollectionsFragment extends ListFragment {
	public static final String KEY_SERVER_INFO = "server_info";
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = super.onCreateView(inflater, container, savedInstanceState);
		setHasOptionsMenu(true);
		return v;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		final ListView listView = getListView();
		listView.setPadding(20, 30, 20, 30);
		
		View header = getActivity().getLayoutInflater().inflate(R.layout.select_collections_header, null);
		listView.addHeaderView(header);
		
		final ServerInfo serverInfo = (ServerInfo)getArguments().getSerializable(KEY_SERVER_INFO);
		final SelectCollectionsAdapter adapter = new SelectCollectionsAdapter(serverInfo);
		setListAdapter(adapter);
		
		listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				int itemPosition = position - 1;	// one list header view at pos. 0
				if (adapter.getItemViewType(itemPosition) == SelectCollectionsAdapter.TYPE_ADDRESS_BOOKS_ROW) {
					// unselect all other address books
					for (int pos = 1; pos <= adapter.getNAddressBooks(); pos++)
						if (pos != itemPosition)
							listView.setItemChecked(pos + 1, false);
				}
				
				getActivity().invalidateOptionsMenu();
			}
		});
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.select_collections, menu);
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
	
	
	// form validation
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		boolean ok = false;
		try {
			ok = getListView().getCheckedItemCount() > 0;
		} catch(IllegalStateException e) {
		}
		MenuItem item = menu.findItem(R.id.add_account);
		item.setEnabled(ok);
	}


	// actions
	
	void addAccount() {
		List<ServerInfo.ResourceInfo> addressBooks = new LinkedList<ServerInfo.ResourceInfo>(),
			calendars = new LinkedList<ServerInfo.ResourceInfo>();
		
		ListAdapter adapter = getListView().getAdapter();
		for (long id : getListView().getCheckedItemIds()) {
			int position = (int)id + 1;		// +1 because header view is inserted at pos. 0 
			ServerInfo.ResourceInfo info = (ServerInfo.ResourceInfo)adapter.getItem(position);
			switch (info.getType()) {
			case ADDRESS_BOOK:
				addressBooks.add(info);
				break;
			case CALENDAR:
				calendars.add(info);
			}
		}
		
		ServerInfo serverInfo = (ServerInfo)getArguments().getSerializable(KEY_SERVER_INFO);
		try {
			URI baseURI = new URI(serverInfo.getBaseURL());
			String accountName = serverInfo.getUserName() + "@" + baseURI.getHost() + baseURI.getPath();
			
			AccountManager accountManager = AccountManager.get(getActivity());
			Account account = new Account(accountName, Constants.ACCOUNT_TYPE);
			Bundle userData = new Bundle();
			userData.putString(Constants.ACCOUNT_KEY_BASE_URL, serverInfo.getBaseURL());
			userData.putString(Constants.ACCOUNT_KEY_USERNAME, serverInfo.getUserName());
			userData.putString(Constants.ACCOUNT_KEY_AUTH_PREEMPTIVE, Boolean.toString(serverInfo.isAuthPreemptive()));
			
			if (!addressBooks.isEmpty()) {
				userData.putString(Constants.ACCOUNT_KEY_ADDRESSBOOK_PATH, addressBooks.get(0).getPath());
				ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
				ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
			} else
				ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 0);
			
			if (accountManager.addAccountExplicitly(account, serverInfo.getPassword(), userData)) {
				// account created, now create calendars
				if (!calendars.isEmpty()) {
					for (ServerInfo.ResourceInfo calendar : calendars)
						LocalCalendar.create(account, getActivity().getContentResolver(), calendar);
					ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1);
					ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true);
				} else
					ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0);
				
				getActivity().finish();				
			} else
				Toast.makeText(getActivity(), "Couldn't create account (already existing?)", Toast.LENGTH_LONG).show();

		} catch (URISyntaxException e) {
		} catch (RemoteException e) {
		}
	}
}
