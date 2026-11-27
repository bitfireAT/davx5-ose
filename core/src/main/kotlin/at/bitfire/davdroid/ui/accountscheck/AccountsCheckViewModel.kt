/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import android.accounts.Account
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.ui.NotificationRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsCheckViewModel @Inject internal constructor(
    accountStatuses: AccountStatusesUseCase,
    private val androidAccountManager: AndroidAccountManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    notificationRegistry: NotificationRegistry
): ViewModel() {

    /** status of all accounts (updated live); `null` while loading */
    val accounts: StateFlow<List<AccountStatus>?> = accountStatuses()
        .onEach { statuses ->
            // for instance after the user has deleted the orphaned account
            if (statuses.none { it.isProblem })
                notificationRegistry.cancelAccountsCheck()
        }
        .map { statuses ->
            statuses.sortedWith(
                compareByDescending<AccountStatus> { it.isProblem }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Deletes the given (orphaned) Android account. [accounts] updates automatically. */
    fun deleteAndroidAccount(account: Account) {
        viewModelScope.launch(ioDispatcher) {
            androidAccountManager.removeAccount(account)
        }
    }

}
