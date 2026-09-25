/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.content.ContentProviderClient
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageClient

/**
 * [at.bitfire.synctools.storage.BatchOperation] for jtx Board
 */
class JtxBatchOperation(
    client: LocalStorageClient
): BatchOperation(client, maxOperationsPerYieldPoint = null) {
    @Deprecated("Remove once all of at.bitfire.synctools.storage uses LocalStorageClient")
    constructor(providerClient: ContentProviderClient) : this(LocalStorageClient(providerClient))
}
