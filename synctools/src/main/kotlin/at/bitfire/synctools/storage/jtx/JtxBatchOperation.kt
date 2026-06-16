/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.content.ContentProviderClient
import at.bitfire.synctools.storage.BatchOperation

/**
 * [at.bitfire.synctools.storage.BatchOperation] for jtx Board
 */
class JtxBatchOperation(
    providerClient: ContentProviderClient
): BatchOperation(providerClient, maxOperationsPerYieldPoint = null)