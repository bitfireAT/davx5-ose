/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.util.Log;

import java.net.URI;
import java.net.URISyntaxException;

public class TestConstants {
	public static final String ROBOHYDRA_BASE = "http://192.168.0.11:3000/";

	public static URI roboHydra;
	static {
		try {
			roboHydra = new URI(ROBOHYDRA_BASE);
		} catch(URISyntaxException e) {
			Log.wtf("davdroid.test.Constants", "Invalid RoboHydra base URL");
		}
	}
}
