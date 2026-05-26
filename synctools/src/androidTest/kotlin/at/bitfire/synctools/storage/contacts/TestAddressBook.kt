/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues

class TestAddressBook(
    account: Account,
    provider: ContentProviderClient
): AndroidAddressBook<AndroidContact, AndroidGroup>(account, provider, ContactFactory, GroupFactory) {

    object ContactFactory: AndroidContactFactory<AndroidContact> {

        override fun fromProvider(addressBook: AndroidAddressBook<AndroidContact, *>, values: ContentValues) =
                AndroidContact(addressBook, values)

    }


    object GroupFactory: AndroidGroupFactory<AndroidGroup> {

        override fun fromProvider(addressBook: AndroidAddressBook<*, AndroidGroup>, values: ContentValues) =
                AndroidGroup(addressBook, values)

    }

}