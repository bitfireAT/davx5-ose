/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.adapter

import android.os.IBinder

/**
 * Interface for an Android sync adapter, as created by [SyncAdapterService].
 *
 * Sync adapters are bound services that communicate over IPC, so the only method is
 * [getBinder], which returns the sync adapter binder.
 */
interface SyncAdapter {

    fun getBinder(): IBinder

}