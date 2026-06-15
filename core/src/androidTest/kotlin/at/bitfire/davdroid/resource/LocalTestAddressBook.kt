/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.Context
import at.bitfire.davdroid.R
import at.bitfire.synctools.vcard.GroupMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

class LocalTestAddressBook @Inject constructor(
    @ApplicationContext private val context: Context,
    private val factory: LocalAddressBook.Factory
) {

    fun provide(
        account: Account,
        provider: ContentProviderClient,
        groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS,
        block: (LocalAddressBook) -> Unit
    ) {
        val accountType = context.getString(R.string.account_type_address_book)
        val abAccount = Account("Test Address Book ${UUID.randomUUID()}", accountType)
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
