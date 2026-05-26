/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
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