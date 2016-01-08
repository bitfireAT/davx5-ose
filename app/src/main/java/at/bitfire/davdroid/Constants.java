/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid;

import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
	public static final String
		ACCOUNT_TYPE = "bitfire.at.davdroid";

    public static final Logger log = LoggerFactory.getLogger("davdroid");

    // notification IDs
    public final static int
            NOTIFICATION_ANDROID_VERSION_UPDATED = 0,
            NOTIFICATION_ACCOUNT_SETTINGS_UPDATED = 1,
            NOTIFICATION_CONTACTS_SYNC = 10,
            NOTIFICATION_CALENDAR_SYNC = 11,
            NOTIFICATION_TASK_SYNC = 12;

    public final static Uri webUri = Uri.parse("https://davdroid.bitfire.at/?pk_campaign=davdroid-app");
}
