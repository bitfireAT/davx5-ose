/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.TaskProvider
import org.dmfs.tasks.contract.TaskContract
import org.junit.Test

class TasksBatchOperationTest(
    providerName: TaskProvider.ProviderName
) : DmfsStyleProvidersTaskTest(providerName) {

    private val testAccount = Account(javaClass.name, TaskContract.LOCAL_ACCOUNT_TYPE)

    @Test(expected = LocalStorageException::class)
    fun testTasksProvider_OperationsPerYieldPoint_500_WithoutMax() {
        val batch = BatchOperation(provider, maxOperationsPerYieldPoint = null)
        val taskList = TestTaskList.create(testAccount, providerName, provider)
        try {
            // 500 operations should fail with BatchOperation(maxOperationsPerYieldPoint = null) (max. 499)
            repeat(500) { idx ->
                batch += BatchOperation.CpoBuilder.newInsert(TaskContract.Tasks.getContentUri(providerName.authority)!!)
                    .withValue(TaskContract.Tasks.LIST_ID, taskList.id)
                    .withValue(TaskContract.Tasks.TITLE, "Task $idx")
            }
            batch.commit()
        } finally {
            taskList.delete()
        }
    }

    @Test
    fun testTasksProvider_OperationsPerYieldPoint_501() {
        val batch = TasksBatchOperation(provider)
        val taskList = TestTaskList.create(testAccount, providerName, provider)
        try {
            // 501 operations should succeed with ContactsBatchOperation
            repeat(501) { idx ->
                batch += BatchOperation.CpoBuilder.newInsert(TaskContract.Tasks.getContentUri(providerName.authority)!!)
                    .withValue(TaskContract.Tasks.LIST_ID, taskList.id)
                    .withValue(TaskContract.Tasks.TITLE, "Task $idx")
            }
            batch.commit()
        } finally {
            taskList.delete()
        }
    }


}