/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.Context
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import org.junit.Assert.assertTrue
import java.util.logging.Logger

class LocalTestAddressBook @AssistedInject constructor(
    @Assisted provider: ContentProviderClient,
    @Assisted override val groupMethod: GroupMethod,
    @ApplicationContext context: Context,
    accountSettingsFactory: AccountSettings.Factory,
    collectionRepository: DavCollectionRepository,
    logger: Logger,
    serviceRepository: DavServiceRepository
): LocalAddressBook(ACCOUNT, provider, context, accountSettingsFactory, collectionRepository, logger, serviceRepository) {

    @AssistedFactory
    interface Factory {
        fun create(provider: ContentProviderClient, groupMethod: GroupMethod): LocalTestAddressBook
    }

    override var readOnly: Boolean
        get() = false
        set(_) = throw NotImplementedError()


    fun clear() {
        for (contact in queryContacts(null, null))
            contact.delete()
        for (group in queryGroups(null, null))
            group.delete()
    }


    companion object {

        val ACCOUNT = Account("LocalTestAddressBook", "at.bitfire.davdroid.test")

        fun createAccount(context: Context) {
            val am = AccountManager.get(context)
            assertTrue("Couldn't create account for local test address-book", am.addAccountExplicitly(ACCOUNT, null, null))
        }

    }

}