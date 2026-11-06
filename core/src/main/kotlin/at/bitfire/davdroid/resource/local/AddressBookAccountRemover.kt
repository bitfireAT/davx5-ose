/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.local

import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.repository.OnBeforeDeleteListener
import javax.inject.Inject

class AddressBookAccountRemover @Inject constructor(
    private val addressBookAccountManager: AddressBookAccountManager,
) : OnBeforeDeleteListener {

    override fun onBeforeDelete(accountId: AccountId) {
        addressBookAccountManager.deleteByAccountId(accountId)
    }
}
