/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.resource.ServerInfo;

public class AddAccountActivity extends Activity {
	final private static String KEY_SERVER_INFO = "serverInfo";

	protected ServerInfo serverInfo;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null)
			serverInfo = (ServerInfo)savedInstanceState.getSerializable(KEY_SERVER_INFO);

		setContentView(R.layout.setup_add_account);
		
		if (savedInstanceState == null) {	// first call
			getFragmentManager().beginTransaction()
				.add(R.id.right_pane, new LoginTypeFragment())
				.commit();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putSerializable(KEY_SERVER_INFO, serverInfo);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.setup_add_account, menu);
		return true;
	}

	public void showHelp(MenuItem item) {
		startActivityForResult(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.WEB_URL_HELP +  "&pk_kwd=add-account-activity")), 0);
	}

}
