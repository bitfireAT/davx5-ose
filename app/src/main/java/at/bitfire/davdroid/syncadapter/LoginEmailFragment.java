/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import java.net.URI;
import java.net.URISyntaxException;

import at.bitfire.davdroid.R;

public class LoginEmailFragment extends Fragment implements TextWatcher {
	
	protected EditText editEmail, editPassword; 
	

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.login_email, container, false);

		editEmail = (EditText)v.findViewById(R.id.email_address);
		editEmail.addTextChangedListener(this);
		editPassword = (EditText)v.findViewById(R.id.password);
		editPassword.addTextChangedListener(this);
		
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
			FragmentTransaction ft = getFragmentManager().beginTransaction();
			
			Bundle args = new Bundle();
			String email = editEmail.getText().toString();
			args.putString(QueryServerDialogFragment.EXTRA_BASE_URI, "mailto:" + email);
			args.putString(QueryServerDialogFragment.EXTRA_USER_NAME, email);
			args.putString(QueryServerDialogFragment.EXTRA_PASSWORD, editPassword.getText().toString());
			args.putBoolean(QueryServerDialogFragment.EXTRA_AUTH_PREEMPTIVE, true);
			
			DialogFragment dialog = new QueryServerDialogFragment();
			dialog.setArguments(args);
		    dialog.show(ft, QueryServerDialogFragment.class.getName());
			break;
		default:
			return false;
		}
		return true;
	}

	
	// input validation
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		boolean	emailOk = false,
				passwordOk = editPassword.getText().length() > 0;

		String email = editEmail.getText().toString();
		try {
			URI uri = new URI("mailto:" + email);
			if (uri.isOpaque()) {
				int pos = email.lastIndexOf("@");
				if (pos != -1)
					emailOk = !email.substring(pos+1).isEmpty();
			}
		} catch (URISyntaxException e) {
			// invalid mailto: URI
		}
		
		MenuItem item = menu.findItem(R.id.next);
		item.setEnabled(emailOk && passwordOk);
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
