/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import at.bitfire.davdroid.startup.StartupAction
import at.bitfire.davdroid.startup.StartupAction.Companion.PRIORITY_DEFAULT
import dagger.Lazy
import javax.inject.Inject

/**
 * Initializes [AndroidAccountRemover].
 */
class AndroidAccountRemoverSetup @Inject constructor(
    private val androidAccountRemover: Lazy<AndroidAccountRemover>
) : StartupAction {

    override val priority = PRIORITY_DEFAULT

    override fun onAppCreate() {
        androidAccountRemover.get().initialize()
    }
}
