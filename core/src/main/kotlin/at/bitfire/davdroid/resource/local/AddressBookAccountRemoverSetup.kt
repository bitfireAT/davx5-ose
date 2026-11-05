/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.local

import at.bitfire.davdroid.startup.StartupAction
import at.bitfire.davdroid.startup.StartupAction.Companion.PRIORITY_DEFAULT
import dagger.Lazy
import javax.inject.Inject

/**
 * Initializes [AddressBookAccountRemover].
 */
class AddressBookAccountRemoverSetup @Inject constructor(
    private val addressBookAccountRemover: Lazy<AddressBookAccountRemover>
) : StartupAction {

    override val priority = PRIORITY_DEFAULT

    override fun onAppCreate() {
        addressBookAccountRemover.get().initialize()
    }
}
