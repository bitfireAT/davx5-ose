/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Base class for [CoroutineWorker]s whose work should run on [IoDispatcher] instead of
 * [CoroutineWorker]'s default (CPU-core-sized [kotlinx.coroutines.Dispatchers.Default]),
 * which can be exhausted by concurrent background work easily and cause deadlocks.
 *
 * This class mainly exists because using [kotlinx.coroutines.Dispatchers.Default] for multiple
 * concurrent syncs causes a deadlock:
 * https://github.com/bitfireAT/davx5/issues/937 – caused by
 * https://youtrack.jetbrains.com/issue/KTOR-9722/DigestAuthProvider-cannot-be-initialized-with-a-congested-Dispatchers.Default-pool
 *
 * As soon as the Ktor upstream bug is fixed, we can switch back to the default dispatcher again.
 * We can also at any time switch to another dispatcher – the only important thing is that there's a free
 * default dispatcher thread when the Ktor DigestAuthenticator is initalized.
 *
 * **Requires Hilt member injection**, so subclasses must be constructed as ([androidx.hilt.work.HiltWorker]).
 */
abstract class IoCoroutineWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        doIoWork()
    }

    abstract suspend fun doIoWork(): Result

}
