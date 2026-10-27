/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.calendar

import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageClient

/**
 * [at.bitfire.synctools.storage.BatchOperation] for the Android calendar provider
 */
class CalendarBatchOperation(
    client: LocalStorageClient
): BatchOperation(client, maxOperationsPerYieldPoint = null)
