/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DAVUtils {
	private static final String TAG = "davdroid.DAVutils";

	public static int CalDAVtoARGBColor(String davColor) {
		int color = 0xFFC3EA6E;		// fallback: "DAVdroid green"
		if (davColor != null) {
			Pattern p = Pattern.compile("#?(\\p{XDigit}{6})(\\p{XDigit}{2})?");
			Matcher m = p.matcher(davColor);
			if (m.find()) {
				int color_rgb = Integer.parseInt(m.group(1), 16);
				int color_alpha = m.group(2) != null ? (Integer.parseInt(m.group(2), 16) & 0xFF) : 0xFF;
				color = (color_alpha << 24) | color_rgb;
			} else
				Log.w(TAG, "Couldn't parse color " + davColor + ", using DAVdroid green");
		}
		return color;
	}

}
