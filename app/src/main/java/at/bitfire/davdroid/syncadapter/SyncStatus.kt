/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import at.bitfire.davdroid.resource.LocalAddressBook

enum class SyncStatus {
    ACTIVE, PENDING, IDLE;

    companion object {
        /**
         * Returns the sync status of a given account. Checks the account itself and possible
         * sub-accounts (address book accounts).
         *
         * @param authorities sync authorities to check (usually taken from [syncAuthorities])
         *
         * @return sync status of the given account
         */
        fun fromAccount(context: Context, authorities: Iterable<String>, account: Account): SyncStatus {
            // check sync framework syncs are active or pending
            if (authorities.any { ContentResolver.isSyncActive(account, it) })
                return SyncStatus.ACTIVE
            val addrBookAccounts = LocalAddressBook.findAll(context, null, account).map { it.account }
            if (addrBookAccounts.any { ContentResolver.isSyncActive(it, ContactsContract.AUTHORITY) })
                return SyncStatus.ACTIVE
            if (authorities.any { ContentResolver.isSyncPending(account, it) } ||
                addrBookAccounts.any { ContentResolver.isSyncPending(it, ContactsContract.AUTHORITY) })
                return SyncStatus.PENDING

            // Also check SyncWorkers
            val workerNames = authorities.map { authority ->
                SyncWorker.workerName(account, authority)
            }
            val workQuery = WorkQuery.Builder
                .fromUniqueWorkNames(workerNames)
                .addStates(listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED))
                .build()
            val workInfos = WorkManager.getInstance(context).getWorkInfos(workQuery).get()
            when {
                workInfos.any { workInfo ->
                    workInfo.state == WorkInfo.State.RUNNING
                } -> return SyncStatus.ACTIVE

                workInfos.any { workInfo ->
                    workInfo.state == WorkInfo.State.ENQUEUED
                } -> return SyncStatus.PENDING
            }

            // None active or pending? Then we're idle ..
            return SyncStatus.IDLE
        }
    }

}