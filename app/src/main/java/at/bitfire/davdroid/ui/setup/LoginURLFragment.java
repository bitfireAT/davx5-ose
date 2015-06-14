/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup;

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
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

import at.bitfire.davdroid.R;

public class LoginURLFragment extends Fragment implements TextWatcher {
	
	protected Spinner spnrScheme;
	protected TextView textHttpWarning;
	protected EditText editBaseURI, editUserName, editPassword;
	protected CheckBox checkboxPreemptive;


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.setup_login_url, container, false);
		
		// protocol selection spinner
		textHttpWarning = (TextView)v.findViewById(R.id.http_warning);
		
		spnrScheme = (Spinner)v.findViewById(R.id.login_scheme);
		spnrScheme.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				String scheme = parent.getAdapter().getItem(position).toString();
				textHttpWarning.setVisibility(scheme.equals("https://") ? View.GONE : View.VISIBLE);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		spnrScheme.setSelection(1);	// HTTPS

		// other input fields
		editBaseURI = (EditText)v.findViewById(R.id.login_host_path);
		editBaseURI.addTextChangedListener(this);
		
		editUserName = (EditText)v.findViewById(R.id.userName);
		editUserName.addTextChangedListener(this);
		
		editPassword = (EditText)v.findViewById(R.id.password);
		editPassword.addTextChangedListener(this);
		
		checkboxPreemptive = (CheckBox) v.findViewById(R.id.auth_preemptive);
		
		// hook into action bar
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
			try {
				args.putString(QueryServerDialogFragment.EXTRA_BASE_URI, getBaseURI().toString());
			} catch (URISyntaxException e) {
			}
			args.putString(QueryServerDialogFragment.EXTRA_USER_NAME, editUserName.getText().toString());
			args.putString(QueryServerDialogFragment.EXTRA_PASSWORD, editPassword.getText().toString());
			args.putBoolean(QueryServerDialogFragment.EXTRA_AUTH_PREEMPTIVE, checkboxPreemptive.isChecked());
			
			DialogFragment dialog = new QueryServerDialogFragment();
			dialog.setArguments(args);
		    dialog.show(ft, QueryServerDialogFragment.class.getName());
			break;
		default:
			return false;
		}
		return true;
	}
	
	
	private URI getBaseURI() throws URISyntaxException {
		String	scheme = spnrScheme.getSelectedItem().toString(),
				host_path = editBaseURI.getText().toString();
		return new URI(scheme + host_path);
	}

	
	// input validation
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		boolean usernameOk = editUserName.getText().length() > 0,
				passwordOk = editPassword.getText().length() > 0,
				urlOk = false;
				
		// check host name
		try {
			if (!StringUtils.isBlank(getBaseURI().getHost()))
				urlOk = true;
		} catch (Exception e) {
		}
			
		MenuItem item = menu.findItem(R.id.next);
		item.setEnabled(usernameOk && passwordOk && urlOk);
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
