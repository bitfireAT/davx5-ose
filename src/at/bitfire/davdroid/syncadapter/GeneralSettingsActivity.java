package at.bitfire.davdroid.syncadapter;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import at.bitfire.davdroid.R;

public class GeneralSettingsActivity extends Activity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getFragmentManager().beginTransaction()
			.replace(android.R.id.content, new GeneralSettingsFragment())
        	.commit();
	}

	
	public static class GeneralSettingsFragment extends PreferenceFragment {
	    @Override
	    public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        
			getPreferenceManager().setSharedPreferencesMode(Context.MODE_MULTI_PROCESS);
	        addPreferencesFromResource(R.xml.general_settings);
	    }
	}
}
