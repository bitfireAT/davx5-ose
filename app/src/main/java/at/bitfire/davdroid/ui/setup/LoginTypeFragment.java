/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

import at.bitfire.davdroid.R;

public class LoginTypeFragment extends Fragment {
	
	protected RadioButton btnTypeEmail;
	

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.setup_login_type, container, false);
		
		btnTypeEmail = (RadioButton)v.findViewById(R.id.login_type_email);

		setHasOptionsMenu(true);
		
		return v;
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.only_next, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.next:
			Fragment loginFragment = btnTypeEmail.isChecked() ? new LoginEmailFragment() : new LoginURLFragment();
			getFragmentManager().beginTransaction()
				.replace(R.id.right_pane, loginFragment)
				.addToBackStack(null)
				.commitAllowingStateLoss();
			return true;
		default:
			return false;
		}
	}
}
