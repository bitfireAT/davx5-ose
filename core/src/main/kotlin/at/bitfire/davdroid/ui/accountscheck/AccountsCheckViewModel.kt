/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.accounts.AndroidAccountInfo
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.accounts.AndroidAccountManager.Companion.isOrphaned
import at.bitfire.davdroid.db.DbAccount
import at.bitfire.davdroid.repository.DbAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AccountsCheckViewModel @Inject internal constructor(
    androidAccountManager: AndroidAccountManager,
    dbAccountRepository: DbAccountRepository
): ViewModel() {

    /** status of all accounts (updated live); `null` while loading */
    val accounts: StateFlow<List<AccountStatus>?> = combine(
        androidAccountManager.getAllFlow(),
        dbAccountRepository.getAllWithNamesFlow(),
        ::toAccountStatuses
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    companion object {

        @VisibleForTesting
        internal fun toAccountStatuses(
            androidAccounts: List<AndroidAccountInfo>,
            dbAccounts: List<DbAccount>
        ): List<AccountStatus> =
            androidAccounts.mapNotNull { androidAccount ->
                val name = androidAccount.account.name
                when {
                    androidAccount.isLegacy -> AccountStatus.Legacy(name)
                    isOrphaned(name, androidAccount.linkedDbAccountId, dbAccounts) -> null
                    else -> AccountStatus.Linked(name)
                }
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    }

}
