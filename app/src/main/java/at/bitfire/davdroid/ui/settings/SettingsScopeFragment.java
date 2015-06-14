/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.settings;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ListFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;

public class SettingsScopeFragment extends ListFragment {
	Account[] accounts;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final AccountManager manager = AccountManager.get(getActivity());
		accounts = manager.getAccountsByType(Constants.ACCOUNT_TYPE);

		final String[] accountNames = new String[accounts.length];
		for (int i = 0; i < accounts.length; i++)
			accountNames[i] = accounts[i].name;
		setListAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_activated_1, accountNames));

		return super.onCreateView(inflater, container, savedInstanceState);
	}

	public void setLayout(boolean tabletLayout) {
		if (tabletLayout)
			getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		setEmptyText(getString(R.string.settings_no_accounts));
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		l.clearChoices();
		((SettingsActivity)getActivity()).showAccountSettings(accounts[position]);
		l.setItemChecked(position, true);
	}
}
