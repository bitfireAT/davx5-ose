package at.bitfire.davdroid.test;

import java.net.MalformedURLException;
import java.net.URL;

import android.util.Log;

public class Constants {
	public static final String ROBOHYDRA_BASE = "http://10.0.0.11:3000/";
	
	public static URL roboHydra;
	static {
		try {
			roboHydra = new URL(ROBOHYDRA_BASE);
		} catch(MalformedURLException e) {
			Log.wtf("davdroid.test.Constants", "Invalid RoboHydra base URL");
		}
	}
}
