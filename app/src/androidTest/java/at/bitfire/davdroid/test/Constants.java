package at.bitfire.davdroid.test;

import java.net.URI;
import java.net.URISyntaxException;

import android.util.Log;

public class Constants {
	public static final String ROBOHYDRA_BASE = "http://10.0.0.11:3000/";
	
	public static URI roboHydra;
	static {
		try {
			roboHydra = new URI(ROBOHYDRA_BASE);
		} catch(URISyntaxException e) {
			Log.wtf("davdroid.test.Constants", "Invalid RoboHydra base URL");
		}
	}
}
