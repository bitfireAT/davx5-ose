/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.R
import at.bitfire.synctools.vcard.GroupMethod
import java.util.concurrent.atomic.AtomicInteger

object LocalTestAddressBook {

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
    private val counter = AtomicInteger()

    fun provide(
        account: Account,
        provider: ContentProviderClient,
        groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS,
        factory: LocalAddressBook.Factory,
        block: (LocalAddressBook) -> Unit
    ) {
        val accountType = context.getString(R.string.account_type_address_book)
        val abAccount = Account("Test Address Book ${counter.incrementAndGet()}", accountType)
        AccountManager.get(context).addAccountExplicitly(abAccount, null, null)
        val ab = factory.create(account, abAccount, provider, groupMethod)
        try {
            block(ab)
        } finally {
            // use ab.addressBookAccount (not abAccount) to handle renames correctly
            AccountManager.get(context).removeAccountExplicitly(ab.addressBookAccount)
        }
    }

}
