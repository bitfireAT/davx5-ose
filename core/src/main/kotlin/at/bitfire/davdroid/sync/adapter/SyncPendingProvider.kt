/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.adapter

import android.accounts.Account
import android.content.Context
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.accounts.toAndroidAccount
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.sync.SyncDataType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class SyncPendingProvider @Inject constructor(
    private val androidAccountManager: AndroidAccountManager,
    @ApplicationContext private val context: Context,
    private val localAddressBookStore: LocalAddressBookStore,
    private val syncFrameworkIntegration: SyncFrameworkIntegration
) {

    /**
     * Observe whether any of the given data types is currently pending for sync.
     *
     * _Note:_ the sync framework doesn't reliably mark a finished one-time sync as "not pending"
     * anymore, see [SyncAdapterImpl.hasAlwaysPendingIssue].
     *
     * @param accountId [AccountId] of the account to observe sync status for
     * @param dataTypes data types to observe sync status for
     *
     * @return flow emitting true if any of the given data types has a sync pending, false otherwise
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun isSyncPending(accountId: AccountId, dataTypes: Iterable<SyncDataType>): Flow<Boolean> {
        // Determine the pending state for each data type of the account as separate flows
        val pendingStateFlows: List<Flow<Boolean>> = dataTypes.mapNotNull { dataType ->
            // Map datatype to authority
            dataType.currentAuthority(context)?.let { authority ->
                // If checking contacts, we need to check all address book accounts instead of the single main account
                val accountsFlow: Flow<List<Account>> = when (dataType) {
                    SyncDataType.CONTACTS -> {
                        localAddressBookStore.getAddressBookAccountsFlow(accountId.toAndroidAccount())
                    }
                    else -> {
                        val account = androidAccountManager.getAndroidAccount(accountId)
                        flowOf(listOf(account))
                    }
                }

                // Return the pending state flow for accounts with this authority
                syncFrameworkIntegration.anyPendingSyncFlow(accountsFlow, authority)
            }
        }

        // Combine the different per data type pending state flows into one
        return combine(pendingStateFlows) { pendingStates ->
            pendingStates.any { pending -> pending }
        }.distinctUntilChanged()
    }
}
