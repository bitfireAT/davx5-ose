package at.bitfire.davdroid.syncadapter;

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
	
	protected RadioButton btnTypeEmail, btnTypeURL;
	

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.login_type, container, false);
		
		btnTypeEmail = (RadioButton)v.findViewById(R.id.login_type_email);
		btnTypeURL = (RadioButton)v.findViewById(R.id.login_type_url);
		
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
				.replace(R.id.fragment_container, loginFragment)
				.addToBackStack(null)
				.commitAllowingStateLoss();
			return true;
		default:
			return false;
		}
	}
}
