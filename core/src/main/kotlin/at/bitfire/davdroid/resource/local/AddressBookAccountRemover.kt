/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.local

import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.OnBeforeDeleteListener
import dagger.Lazy
import javax.inject.Inject

class AddressBookAccountRemover @Inject constructor(
    private val accountRepository: AccountRepository,
    private val addressBookAccountManager: Lazy<AddressBookAccountManager>,
) : OnBeforeDeleteListener {

    fun initialize() {
        accountRepository.addOnBeforeDeleteListener(this)
    }

    override fun onBeforeDelete(accountId: AccountId) {
        addressBookAccountManager.get().deleteByAccountId(accountId)
    }
}
