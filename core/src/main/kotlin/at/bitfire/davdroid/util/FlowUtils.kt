/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.flatMapMerge

/**
 * Splits this flow into batches and processes each batch with [process].
 *
 * Batches are processed concurrently, so the result order across batches isn't guaranteed.
 *
 * @param batchSize The size of each batch.
 * @param process A function that takes a batch (list of [I]) and returns a Flow of [O].
 *
 * @return A Flow of [O] resulting from applying [process] to each batch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <I, O> Flow<I>.batchMap(
    batchSize: Int,
    process: (List<I>) -> Flow<O>
): Flow<O> =
    chunked(batchSize).flatMapMerge { process(it) }
