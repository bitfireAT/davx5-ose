/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.net.Uri
import org.dmfs.tasks.contract.TaskContract

/**
 * How synctools uses tasks.org task sync columns and data rows.
 */
object DmfsTasksContract {

    fun Uri.asSyncAdapter(account: Account): Uri = buildUpon()
        .appendQueryParameter(TaskContract.ACCOUNT_NAME, account.name)
        .appendQueryParameter(TaskContract.ACCOUNT_TYPE, account.type)
        .appendQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER, "true")
        .build()


    /**
     * Custom sync column to store the last known ETag of a task.
     *
     * Type: [String]
     */
    const val COLUMN_ETAG = TaskContract.Tasks.SYNC1

    /**
     * Custom sync column to store sync flags of a task.
     *
     * Type: [Int]
     */
    const val COLUMN_FLAGS = TaskContract.Tasks.SYNC2

    /**
     * Data column for storing unknown properties.
     */
    const val UNKNOWN_PROPERTY_DATA = TaskContract.Properties.DATA0

}
