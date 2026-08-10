/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.startup

import androidx.work.WorkManager
import at.bitfire.davdroid.startup.StartupAction.Companion.PRIORITY_LAST
import at.bitfire.davdroid.sync.account.AccountsCleanupWorker
import javax.inject.Inject

/**
 * Enables periodic work that cleans up orphaned accounts in the database.
 */
class EnableAccountsCleanupAction @Inject constructor(
    private val workManager: WorkManager
) : StartupAction {

    override val priority = PRIORITY_LAST

    override fun onAppCreate() {
        AccountsCleanupWorker.enable(workManager)
    }

}
