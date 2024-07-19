/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.content.Context
import androidx.work.WorkerParameters
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.NotificationRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class TestSyncWorker @Inject constructor(
    @ApplicationContext context: Context,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val notificationRegistry: NotificationRegistry
): BaseSyncWorker(
    context,
    workerParams = mockk<WorkerParameters>(),
    accountSettingsFactory,
    notificationRegistry,
    syncDispatcher = Dispatchers.Main
)