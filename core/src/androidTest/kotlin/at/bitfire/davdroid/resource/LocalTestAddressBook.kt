/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.adapter.SyncFrameworkIntegration
import at.bitfire.synctools.vcard.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Optional
import java.util.logging.Logger

/**
 * A local address book that provides an easy way to set the group method in tests.
 */
class LocalTestAddressBook @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted("addressBook") addressBookAccount: Account,
    @Assisted provider: ContentProviderClient,
    @Assisted override val groupMethod: GroupMethod,
    accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext context: Context,
    logger: Logger,
    syncFramework: SyncFrameworkIntegration
): LocalAddressBook(
    account = account,
    _addressBookAccount = addressBookAccount,
    provider = provider,
    groupMethod = groupMethod,
    accountSettingsFactory = accountSettingsFactory,
    context = context,
    dirtyVerifier = Optional.empty(),
    logger = logger,
    syncFramework = syncFramework
) {

    @AssistedFactory
    interface Factory {
        fun create(
            account: Account,
            @Assisted("addressBook") addressBookAccount: Account,
            provider: ContentProviderClient,
            groupMethod: GroupMethod
        ): LocalTestAddressBook
    }

}