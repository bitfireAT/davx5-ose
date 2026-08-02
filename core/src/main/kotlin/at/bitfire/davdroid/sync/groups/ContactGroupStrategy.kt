/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.groups

import at.bitfire.synctools.mapping.contacts.Contact

interface ContactGroupStrategy {

    /**
     * Local-only group housekeeping (e.g. dissolving groups, propagating group-level changes to
     * member contacts) that must happen regardless of whether local changes will be uploaded.
     */
    suspend fun resolveLocalGroupChanges() {}

    /**
     * Prepares group state for upload. Only meaningful when local changes are actually pushed
     * to the server.
     */
    suspend fun beforeUploadDirty() {}

    fun verifyContactBeforeSaving(contact: Contact)
    suspend fun postProcess()

}