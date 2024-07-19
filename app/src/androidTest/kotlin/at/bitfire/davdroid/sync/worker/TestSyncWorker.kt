/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.content.Context
import androidx.work.WorkerParameters
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.SyncConditions
import at.bitfire.davdroid.ui.NotificationRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class TestSyncWorker @Inject constructor(
    @ApplicationContext context: Context,
    accountSettingsFactory: AccountSettings.Factory,
    notificationRegistry: NotificationRegistry,
    syncConditionsFactory: SyncConditions.Factory
): BaseSyncWorker(
    context,
    workerParams = mockk<WorkerParameters>(),
    accountSettingsFactory,
    notificationRegistry,
    syncConditionsFactory,
    syncDispatcher = Dispatchers.Main
)