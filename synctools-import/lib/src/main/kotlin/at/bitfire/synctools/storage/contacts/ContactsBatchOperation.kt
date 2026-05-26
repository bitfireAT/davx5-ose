/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.contacts

import android.content.ContentProviderClient
import at.bitfire.synctools.storage.BatchOperation

/**
 * [BatchOperation] for the Android contacts provider
 */
class ContactsBatchOperation(
    providerClient: ContentProviderClient
): BatchOperation(providerClient, maxOperationsPerYieldPoint = OPERATIONS_PER_YIELD_POINT) {

    companion object {

        /**
         * Maximum number of operations per yield point in contacts provider.
         *
         * See https://android.googlesource.com/platform/packages/providers/ContactsProvider.git/+/refs/heads/android11-release/src/com/android/providers/contacts/AbstractContactsProvider.java#70
         */
        const val OPERATIONS_PER_YIELD_POINT = 499

    }

}