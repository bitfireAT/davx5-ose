/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.groups

import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.synctools.mapping.contacts.Contact
import java.util.Optional
import java.util.logging.Logger

class CategoriesStrategy(val addressBook: LocalAddressBook): ContactGroupStrategy {

    private val logger: Logger
        get() = Logger.getGlobal()

    override suspend fun beforeUploadDirty() {
        // groups with DELETED=1: set all members to dirty, then remove group
        addressBook.findDeletedGroups().collect { group ->
            logger.fine("Finally removing group $group")
            group.markMembersDirty()
            group.androidGroup.delete()
        }

        // groups with DIRTY=1: mark all members as dirty, then clean DIRTY flag of group
        addressBook.findDirtyGroups().collect { group ->
            logger.fine("Marking members of modified group $group as dirty")
            group.markMembersDirty()
            group.clearDirty(Optional.empty(), null)
        }
    }

    override fun verifyContactBeforeSaving(contact: Contact) {
        if (contact.group || contact.members.isNotEmpty()) {
            logger.warning("Received group vCard although group method is CATEGORIES. Saving as regular contact")
            contact.group = false
            contact.members.clear()
        }
    }

    override suspend fun postProcess() {
        logger.info("Removing empty groups")
        addressBook.ab.deleteGroupsWithoutMembers()
    }

}