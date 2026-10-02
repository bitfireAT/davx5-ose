/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.tasks

import android.content.ContentProviderClient
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageClient

/**
 * [at.bitfire.synctools.storage.BatchOperation] for the tasks.org / OpenTasks provider
 */
class TasksBatchOperation(
    client: LocalStorageClient
) : BatchOperation(client, maxOperationsPerYieldPoint = OPERATIONS_PER_YIELD_POINT) {
    @Deprecated("Remove once all of at.bitfire.synctools.storage uses LocalStorageClient")
    constructor(providerClient: ContentProviderClient) : this(LocalStorageClient(providerClient))

    companion object {

        /**
         * Maximum number of operations per yield point in tasks.org / OpenTasks task providers.
         * (Does not apply for jtxBoard.)
         */
        const val OPERATIONS_PER_YIELD_POINT = 499

    }

}