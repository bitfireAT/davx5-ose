/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage

import android.accounts.Account
import at.bitfire.ical4android.DmfsStyleProvidersTaskTest
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.impl.TestTaskList
import at.bitfire.synctools.storage.tasks.TasksBatchOperation
import at.bitfire.synctools.test.BuildConfig
import org.dmfs.tasks.contract.TaskContract
import org.junit.Test

class TasksBatchOperationTest(
    providerName: TaskProvider.ProviderName
): DmfsStyleProvidersTaskTest(providerName) {

    private val testAccount = Account(javaClass.name, BuildConfig.APPLICATION_ID)

    @Test(expected = LocalStorageException::class)
    fun testTasksProvider_OperationsPerYieldPoint_500_WithoutMax() {
        val batch = BatchOperation(provider.client, maxOperationsPerYieldPoint = null)
        val taskList = TestTaskList.create(testAccount, provider)
        try {
            // 500 operations should fail with BatchOperation(maxOperationsPerYieldPoint = null) (max. 499)
            repeat(500) { idx ->
                batch += BatchOperation.CpoBuilder.newInsert(provider.tasksUri())
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
        val batch = TasksBatchOperation(provider.client)
        val taskList = TestTaskList.create(testAccount, provider)
        try {
            // 501 operations should succeed with ContactsBatchOperation
            repeat(501) { idx ->
                batch += BatchOperation.CpoBuilder.newInsert(provider.tasksUri())
                    .withValue(TaskContract.Tasks.LIST_ID, taskList.id)
                    .withValue(TaskContract.Tasks.TITLE, "Task $idx")
            }
            batch.commit()
        } finally {
            taskList.delete()
        }
    }


}