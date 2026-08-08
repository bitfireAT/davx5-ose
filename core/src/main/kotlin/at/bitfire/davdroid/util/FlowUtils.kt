/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.flatMapConcat

/**
 * Processes this flow in batches, applying the [block] function to each batch.
 *
 * @param batchSize The size of each batch.
 * @param block A function that takes a batch (list of [I]) and returns a Flow of [O].
 *
 * @return A Flow of [O] resulting from applying [block] to each batch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <I, O> Flow<I>.batchMap(
    batchSize: Int,
    block: (List<I>) -> Flow<O>
): Flow<O> =
    chunked(batchSize).flatMapConcat { block(it) }
