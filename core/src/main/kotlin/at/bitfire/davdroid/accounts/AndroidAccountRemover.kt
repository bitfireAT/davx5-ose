/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.OnBeforeDeleteListener
import dagger.Lazy
import javax.inject.Inject

class AndroidAccountRemover @Inject constructor(
    private val accountRepository: AccountRepository,
    private val androidAccountManager: Lazy<AndroidAccountManager>,
) : OnBeforeDeleteListener {

    fun initialize() {
        accountRepository.addOnBeforeDeleteListener(this)
    }

    override fun onBeforeDelete(accountId: AccountId) {
        if (accountId is DbAccountId) {
            androidAccountManager.get().removeAccount(accountId)
        }
    }
}
