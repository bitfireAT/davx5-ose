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
 * Use as base class for workers that primarily do I/O work.
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
