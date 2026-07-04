/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.groups

import android.content.ContentUris
import android.provider.ContactsContract
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.sync.ContactsSyncManager.Companion.disjunct
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.contacts.AddressContract.asSyncAdapter
import at.bitfire.synctools.storage.contacts.ContactsBatchOperation
import java.io.FileNotFoundException
import java.util.logging.Level
import java.util.logging.Logger

class VCard4Strategy(val addressBook: LocalAddressBook): ContactGroupStrategy {

    private val logger: Logger
        get() = Logger.getGlobal()

    override suspend fun beforeUploadDirty() {
        /* Mark groups with changed members as dirty:
           1. Iterate over all dirty contacts.
           2. Check whether group memberships have changed by comparing group memberships and cached group memberships.
           3. Mark groups which have been added to/removed from the contact as dirty so that they will be uploaded.
           4. Successful upload will reset dirty flag and update cached group memberships.
         */
        val batch = ContactsBatchOperation(addressBook.ab.provider)
        addressBook.findDirtyContacts().collect { contact ->
            try {
                logger.log(
                    Level.FINE,
                    "Looking for changed group memberships of contact {0}",
                    arrayOf(contact.fileName)
                )
                val cachedGroups = contact.androidContact.getCachedGroupMemberships()
                val currentGroups = contact.androidContact.getGroupMemberships()
                for (groupID in cachedGroups disjunct currentGroups) {
                    logger.fine("Marking group as dirty: $groupID")
                    batch += BatchOperation.CpoBuilder
                        .newUpdate(ContentUris.withAppendedId(ContactsContract.Groups.CONTENT_URI, groupID).asSyncAdapter())
                        .withValue(ContactsContract.Groups.DIRTY, 1)
                }
            } catch(_: FileNotFoundException) {
            }
        }
        batch.commit()
    }

    override fun verifyContactBeforeSaving(contact: Contact) {
    }

    override suspend fun postProcess() {
        addressBook.applyPendingMemberships()
    }

}