/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.util.Log;

import at.bitfire.davdroid.R;

public class SettingsActivity extends Activity {
	private final static String KEY_SELECTED_ACCOUNT = "selected_account";

	protected Account selectedAccount;
	boolean tabletLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_settings);

		tabletLayout = findViewById(R.id.right_pane) != null;
		if (!tabletLayout)
			getFragmentManager().beginTransaction()
					.add(R.id.content_pane, new SettingsScopeFragment())
					.commit();

		if (savedInstanceState != null) {
			selectedAccount = savedInstanceState.getParcelable(KEY_SELECTED_ACCOUNT);
			if (selectedAccount != null)
				showAccountSettings(selectedAccount);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putParcelable(KEY_SELECTED_ACCOUNT, selectedAccount);
		super.onSaveInstanceState(outState);
	}

	void showAccountSettings(Account account) {
		selectedAccount = account;

		FragmentManager fm = getFragmentManager();
		Fragment settingsFragment = new SettingsAccountFragment();
		Bundle args = new Bundle();
		args.putParcelable(SettingsAccountFragment.KEY_ACCOUNT, account);
		settingsFragment.setArguments(args);

		FragmentTransaction ft = fm
				.beginTransaction()
				.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

		if (tabletLayout)
			ft  .replace(R.id.right_pane, settingsFragment);
		else	// phone layout
			ft  .replace(R.id.content_pane, settingsFragment)
				.addToBackStack(null);

		ft.commit();
	}
}
