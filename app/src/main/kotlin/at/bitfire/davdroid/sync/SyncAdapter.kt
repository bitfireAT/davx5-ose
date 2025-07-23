/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.os.IBinder

interface SyncAdapter {

    fun getBinder(): IBinder

}