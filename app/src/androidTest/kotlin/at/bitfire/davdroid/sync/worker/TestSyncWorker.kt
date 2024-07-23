/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.content.Context
import androidx.work.WorkerParameters
import dagger.hilt.android.qualifiers.ApplicationContext
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class TestSyncWorker @Inject constructor(
    @ApplicationContext context: Context
): BaseSyncWorker(
    context,
    workerParams = mockk<WorkerParameters>(),
    syncDispatcher = Dispatchers.Main
)