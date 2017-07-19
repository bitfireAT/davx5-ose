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
