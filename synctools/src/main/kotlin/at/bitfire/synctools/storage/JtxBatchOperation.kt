/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage

import android.content.ContentProviderClient

/**
 * [BatchOperation] for jtx Board
 */
class JtxBatchOperation(
    providerClient: ContentProviderClient
): BatchOperation(providerClient, maxOperationsPerYieldPoint = null)