/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid;

import android.net.Uri;

public class Constants {

	public static final String
		ACCOUNT_TYPE = "bitfire.at.davdroid";

    // notification IDs
    public final static int
            NOTIFICATION_EXTERNAL_FILE_LOGGING = 1,
            NOTIFICATION_REFRESH_COLLECTIONS = 2,
            NOTIFICATION_CONTACTS_SYNC = 10,
            NOTIFICATION_CALENDAR_SYNC = 11,
            NOTIFICATION_TASK_SYNC = 12,
            NOTIFICATION_PERMISSIONS = 20;

    public static final Uri webUri = Uri.parse("https://davdroid.bitfire.at/?pk_campaign=davdroid-app");

    public static final int DEFAULT_SYNC_INTERVAL = 4 * 3600;  // 4 hours

}
