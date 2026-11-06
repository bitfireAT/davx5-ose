/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import at.bitfire.davdroid.repository.OnBeforeDeleteListener
import javax.inject.Inject

class AndroidAccountRemover @Inject constructor(
    private val androidAccountManager: AndroidAccountManager,
) : OnBeforeDeleteListener {

    override fun onBeforeDelete(accountId: AccountId) {
        if (accountId is DbAccountId) {
            androidAccountManager.removeAccount(accountId)
        }
    }
}
