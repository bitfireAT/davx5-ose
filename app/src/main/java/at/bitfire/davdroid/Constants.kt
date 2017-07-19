/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid;

object Constants {

    // notification IDs
    @JvmField val NOTIFICATION_EXTERNAL_FILE_LOGGING = 1
    @JvmField val NOTIFICATION_REFRESH_COLLECTIONS = 2
    @JvmField val NOTIFICATION_CONTACTS_SYNC = 10
    @JvmField val NOTIFICATION_CALENDAR_SYNC = 11
    @JvmField val NOTIFICATION_TASK_SYNC = 12
    @JvmField val NOTIFICATION_PERMISSIONS = 20
    @JvmField val NOTIFICATION_SUBSCRIPTION = 21

    @JvmField
    val DEFAULT_SYNC_INTERVAL = 4 * 3600L    // 4 hours

}
