/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.tasks

import android.content.ContentProviderClient
import at.bitfire.synctools.storage.BatchOperation

/**
 * [at.bitfire.synctools.storage.BatchOperation] for the tasks.org / OpenTasks provider
 */
class TasksBatchOperation(
    providerClient: ContentProviderClient
) : BatchOperation(providerClient, maxOperationsPerYieldPoint = OPERATIONS_PER_YIELD_POINT) {

    companion object {

        /**
         * Maximum number of operations per yield point in tasks.org / OpenTasks task providers.
         * (Does not apply for jtxBoard.)
         */
        const val OPERATIONS_PER_YIELD_POINT = 499

    }

}