/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.content.ContentProviderClient
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.TaskProvider
import org.dmfs.tasks.contract.TaskContract

object TestTaskList {

    /**
     * Creates a test task list on the given test account.
     *
     * @param account       test account (must have a real account type or [TaskContract.LOCAL_ACCOUNT_TYPE],
     *                      otherwise the provider may immediately remove the account again)
     * @param client        content provider client to use
     * @param providerName  task provider name
     * @return the created test task List (don't forget to delete it when tests are finished)
     */
    fun create(account: Account, providerName: TaskProvider.ProviderName, client: ContentProviderClient): DmfsTaskList {
        val values = contentValuesOf(
            TaskContract.TaskListColumns.LIST_NAME to "Test Task List",
            TaskContract.TaskListColumns.LIST_COLOR to 0xffff0000,
            TaskContract.TaskListColumns.SYNC_ENABLED to 1,
            TaskContract.TaskListColumns.VISIBLE to 1
        )

        val dmfsTaskListProvider = DmfsTaskListProvider(account, client, providerName)
        return dmfsTaskListProvider.createAndGetTaskList(values)
    }

}
