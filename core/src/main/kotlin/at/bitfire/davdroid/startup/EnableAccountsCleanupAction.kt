/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.startup

import android.content.Context
import at.bitfire.davdroid.di.qualifier.ApplicationScope
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.startup.StartupAction.Companion.PRIORITY_LAST
import at.bitfire.davdroid.sync.account.AccountsCleanupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Enables periodic work that cleans up orphaned accounts in the database.
 */
class EnableAccountsCleanupAction @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : StartupAction {

    override val priority = PRIORITY_LAST

    override fun onAppCreate() {
        applicationScope.launch(ioDispatcher) {
            AccountsCleanupWorker.enable(context)
        }
    }

}
