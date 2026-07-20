/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.startup

import android.content.Context
import at.bitfire.davdroid.startup.StartupAction.Companion.PRIORITY_LAST
import at.bitfire.davdroid.sync.account.AccountsCleanupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Enables periodic work that cleans up orphaned accounts in the database.
 */
class EnableAccountsCleanupAction @Inject constructor(
    @ApplicationContext private val context: Context
) : StartupAction {

    override val priority = PRIORITY_LAST

    override fun onAppCreate() {
        AccountsCleanupWorker.enable(context)
    }

}
