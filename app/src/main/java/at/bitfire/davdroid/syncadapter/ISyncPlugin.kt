/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter

import android.content.Context
import android.content.SyncResult

interface ISyncPlugin {

    /**
     * Called before synchronization within a sync adapter is started. Can be used for
     * license checks etc. Must be thread-safe.
     * @return whether synchronization shall take place (false to abort)
     */
    fun beforeSync(context: Context, syncResult: SyncResult): Boolean

    /**
     * Called at the end of a synchronization adapter call, regardless of whether the synchronization
     * was actually run (i.e. what [beforeSync] had returned). Must be thread-safe.
     */
    fun afterSync(context: Context, syncResult: SyncResult)

}
