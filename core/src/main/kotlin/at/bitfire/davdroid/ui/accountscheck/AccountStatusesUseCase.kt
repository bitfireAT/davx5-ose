/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.repository.DbAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class AccountStatusesUseCase @Inject internal constructor(
    private val androidAccountManager: AndroidAccountManager,
    private val dbAccountRepository: DbAccountRepository
) {

    /**
     * Returns the (unsorted) status of all DAVx⁵ Android accounts, updated live.
     */
    operator fun invoke(): Flow<List<AccountStatus>> = combine(
        androidAccountManager.getAllFlow(),
        dbAccountRepository.getAllDbAccountsFlow()  // only to check again when DB accounts change
    ) { androidAccounts, _ ->
        androidAccounts.map { androidAccount ->
            val name = androidAccount.account.name
            when {
                androidAccount.isLegacy -> AccountStatus.Legacy(name)
                androidAccountManager.isOrphaned(androidAccount.linkedDbAccountId) -> AccountStatus.MissingDbAccount(androidAccount.account)
                else -> AccountStatus.Linked(name)
            }
        }
    }

}
