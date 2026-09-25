/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.content.ContentProviderClient
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageClient

/**
 * [BatchOperation] for the Android contacts provider
 */
class ContactsBatchOperation(
    client: LocalStorageClient
): BatchOperation(client, maxOperationsPerYieldPoint = OPERATIONS_PER_YIELD_POINT) {
    @Deprecated("Remove once all of at.bitfire.synctools.storage uses LocalStorageClient")
    constructor(providerClient: ContentProviderClient) : this(LocalStorageClient(providerClient))

    companion object {

        /**
         * Maximum number of operations per yield point in contacts provider.
         *
         * See https://android.googlesource.com/platform/packages/providers/ContactsProvider.git/+/refs/heads/android11-release/src/com/android/providers/contacts/AbstractContactsProvider.java#70
         */
        const val OPERATIONS_PER_YIELD_POINT = 499

    }

}