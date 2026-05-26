/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.calendar

import android.content.ContentProviderClient
import at.bitfire.synctools.storage.BatchOperation

/**
 * [at.bitfire.synctools.storage.BatchOperation] for the Android calendar provider
 */
class CalendarBatchOperation(
    providerClient: ContentProviderClient
): BatchOperation(providerClient, maxOperationsPerYieldPoint = null)