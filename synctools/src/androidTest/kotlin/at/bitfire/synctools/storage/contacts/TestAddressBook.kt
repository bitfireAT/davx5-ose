/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.synctools.storage.LocalStorageClient
import at.bitfire.synctools.test.account.TestAccount
import at.bitfire.synctools.vcard.GroupMethod
import java.util.concurrent.atomic.AtomicInteger

object TestAddressBook {

    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
    private val counter = AtomicInteger()

    fun create(
        client: LocalStorageClient,
        groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS
    ): AndroidAddressBook {
        val account = TestAccount.create("Test Address Book ${counter.incrementAndGet()}")
        return AndroidAddressBook(context, account, client, groupMethod)
    }

    fun remove(addressBook: AndroidAddressBook) = TestAccount.remove(addressBook.addressBookAccount)

}
