/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import at.bitfire.davdroid.accounts.AccountId

/**
 * Interface that is used to be notified when an app account is about to be deleted.
 *
 * @see AccountRepository.addOnBeforeDeleteListener
 * @see AccountRepository.removeOnBeforeDeleteListener
 */
fun interface OnBeforeDeleteListener {
    /**
     * Called right before an app account is deleted.
     */
    fun onBeforeDelete(accountId: AccountId)
}
