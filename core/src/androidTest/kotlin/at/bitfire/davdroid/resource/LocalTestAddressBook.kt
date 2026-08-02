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

    private val accountManager = AccountManager.get(context)

    fun provide(
        account: Account,
        provider: ContentProviderClient,
        groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS,
        block: (LocalAddressBook) -> Unit
    ) {
        val ab = create(account, provider, groupMethod)
        try {
            block(ab)
        } finally {
            delete(ab)
        }
    }

    /**
     * Creates a new local address book for testing purposes.
     *
     * @param account The account to associate with the new address book.
     * @param provider The content provider client to use for the new address book.
     * @param groupMethod The method to use for grouping contacts in the address book.
     * Defaults to [GroupMethod.GROUP_VCARDS].
     * @return The newly created local address book.
     */
    fun create(
        account: Account,
        provider: ContentProviderClient,
        groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS
    ): LocalAddressBook {
        val accountType = context.getString(R.string.account_type_address_book)
        val abAccount = Account("Test Address Book ${UUID.randomUUID()}", accountType)
        accountManager.addAccountExplicitly(abAccount, null, null)
        return factory.create(account, abAccount, provider, groupMethod)
    }

    /**
     * Removes a test address book created by [create].
     */
    fun delete(addressBook: LocalAddressBook) {
        accountManager.removeAccountExplicitly(addressBook.addressBookAccount)
    }

}
