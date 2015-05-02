/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.text.format.Time;
import android.util.Log;

import org.apache.commons.lang.StringUtils;

import java.util.SimpleTimeZone;

public class DateUtils {
    private final static String TAG = "davdroid.DateUtils";

    public static String findAndroidTimezoneID(String tzID) {
        String localTZ = null;
        String availableTZs[] = SimpleTimeZone.getAvailableIDs();

        // first, try to find an exact match (case insensitive)
        for (String availableTZ : availableTZs)
            if (tzID.equalsIgnoreCase(availableTZ)) {
                localTZ = availableTZ;
                break;
            }

        // if that doesn't work, try to find something else that matches
        if (localTZ == null) {
            Log.w(TAG, "Coulnd't find time zone with matching identifiers, trying to guess");
            for (String availableTZ : availableTZs)
                if (StringUtils.indexOfIgnoreCase(tzID, availableTZ) != -1) {
                    localTZ = availableTZ;
                    break;
                }
        }

        // if that doesn't work, use UTC as fallback
        if (localTZ == null) {
            Log.e(TAG, "Couldn't identify time zone, using UTC as fallback");
            localTZ = Time.TIMEZONE_UTC;
        }

        Log.d(TAG, "Assuming time zone " + localTZ + " for " + tzID);
        return localTZ;
    }
}
