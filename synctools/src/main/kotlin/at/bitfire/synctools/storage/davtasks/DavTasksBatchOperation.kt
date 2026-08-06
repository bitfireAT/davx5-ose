/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.davtasks

import android.content.ContentProviderClient
import at.bitfire.synctools.storage.BatchOperation

/**
 * [BatchOperation] for the DAVx⁵-hosted tasks provider.
 */
class DavTasksBatchOperation(
    providerClient: ContentProviderClient
) : BatchOperation(providerClient, maxOperationsPerYieldPoint = OPERATIONS_PER_YIELD_POINT) {

    companion object {
        const val OPERATIONS_PER_YIELD_POINT = 499
    }

}
