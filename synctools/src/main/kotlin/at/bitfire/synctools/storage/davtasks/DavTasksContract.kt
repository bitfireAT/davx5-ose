/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.davtasks

import android.accounts.Account
import android.net.Uri
import at.bitfire.tasks.contract.Tasks
import at.bitfire.tasks.contract.TasksContract

/**
 * How synctools uses the DAVx⁵-hosted tasks provider's sync-adapter query parameters and
 * sync-adapter-private columns.
 */
object DavTasksContract {

    fun Uri.asSyncAdapter(account: Account): Uri = buildUpon()
        .appendQueryParameter(TasksContract.PARAM_ACCOUNT_NAME, account.name)
        .appendQueryParameter(TasksContract.PARAM_ACCOUNT_TYPE, account.type)
        .appendQueryParameter(TasksContract.PARAM_CALLER_IS_SYNCADAPTER, "true")
        .build()

    /**
     * Custom sync column to store the last known ETag of a task.
     *
     * Type: [String]
     */
    const val COLUMN_ETAG = Tasks.SYNC1

    /**
     * Custom sync column to store sync flags of a task.
     *
     * Type: [Int]
     */
    const val COLUMN_FLAGS = Tasks.SYNC2

}
