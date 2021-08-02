package at.bitfire.davdroid.syncadapter.groups

import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddress
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.syncadapter.ContactsSyncManager
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Contact

class CategoriesStrategy(val syncManager: ContactsSyncManager): ContactGroupStrategy {

    private val localCollection = syncManager.localCollection

    override fun prepare() {
    }

    override fun beforeUploadDirty() {
        // groups with DELETED=1: set all members to dirty, then remove group
        for (group in localCollection.findDeletedGroups()) {
            Logger.log.fine("Finally removing group $group")
            group.markMembersDirty()
            group.delete()
        }

        // groups with DIRTY=1: mark all members as dirty, then clean DIRTY flag of group
        for (group in localCollection.findDirtyGroups()) {
            Logger.log.fine("Marking members of modified group $group as dirty")
            group.markMembersDirty()
            group.clearDirty(null, null)
        }
    }

    override fun beforeGenerateUpload(local: LocalAddress) {
        val localContact = local as? LocalContact ?:
            throw IllegalArgumentException("Only LocalContacts can be uploaded with CategoriesStrategy")

        val categories = localContact.getContact().categories
        for (groupID in localContact.getGroupMemberships()) {
            Logger.log.fine("Adding membership in group $groupID as category")
            val group = localCollection.findGroupById(groupID)
            group.getContact().displayName?.let { groupName ->
                categories.add(groupName)
            }
        }
    }

    override fun verifyContactBeforeSaving(contact: Contact) {
        if (contact.group || contact.members.isNotEmpty()) {
            Logger.log.warning("Received group vCard although group method is CATEGORIES. Saving as regular contact")
            contact.group = false
            contact.members.clear()
        }
    }

    override fun afterSavingContact(local: LocalAddress) {
        val localContact = local as? LocalContact ?:
            throw IllegalArgumentException("Only LocalContacts can be uploaded with CategoriesStrategy")

        // VCard3: update group memberships from CATEGORIES
        val batch = BatchOperation(syncManager.provider)
        Logger.log.fine("Removing contact group memberships")
        localContact.removeGroupMemberships(batch)

        for (category in localContact.getContact().categories) {
            val groupID = localCollection.findOrCreateGroup(category)
            Logger.log.fine("Adding membership in group $category ($groupID)")
            localContact.addToGroup(batch, groupID)
        }

        batch.commit()
    }

    override fun postProcess() {
        Logger.log.info("Removing empty groups")
        localCollection.removeEmptyGroups()
    }

}