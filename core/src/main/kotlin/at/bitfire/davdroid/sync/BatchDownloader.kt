/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.remote.WebDavCollection
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.flatMapConcat

/**
 * Groups this flow's [Url]s into batches of [batchSize] and downloads each batch using [multiget]
 * (once as soon as [batchSize] items have been enqueued, and once more with the remainder when
 * the flow completes).
 */
fun Flow<Url>.downloadBatch(
    batchSize: Int = 10,
    multiget: (List<Url>) -> Flow<WebDavCollection.MultiGetItem>
): Flow<WebDavCollection.MultiGetItem> =
    chunked(batchSize).flatMapConcat { multiget(it) }
