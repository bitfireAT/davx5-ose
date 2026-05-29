/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.content.ContentProviderClient

/**
 * [BatchOperation] for jtx Board
 */
class JtxBatchOperation(
    providerClient: ContentProviderClient
): BatchOperation(providerClient, maxOperationsPerYieldPoint = null)