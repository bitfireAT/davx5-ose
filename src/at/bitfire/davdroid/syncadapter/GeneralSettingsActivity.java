/*******************************************************************************
 * Copyright (c) 2014 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package at.bitfire.davdroid.syncadapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import at.bitfire.davdroid.R;

public class GeneralSettingsActivity extends Activity {
	final static String URL_REPORT_ISSUE = "https://github.com/bitfireAT/davdroid/blob/master/CONTRIBUTING.md#reporting-issues"; 
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getFragmentManager().beginTransaction()
			.replace(android.R.id.content, new GeneralSettingsFragment())
        	.commit();
	}
	
	public void reportIssue(MenuItem item) {
		startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(URL_REPORT_ISSUE)));
	}
	
	
	public static class GeneralSettingsFragment extends PreferenceFragment {
	    @Override
	    public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        
			getPreferenceManager().setSharedPreferencesMode(Context.MODE_MULTI_PROCESS);
	        addPreferencesFromResource(R.xml.general_settings);
	        
	        setHasOptionsMenu(true);
	    }
		
		@Override
		public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
			inflater.inflate(R.menu.debug_settings, menu);
		}
	}
}
