package at.bitfire.davdroid.syncadapter.groups

import android.content.ContentUris
import android.provider.ContactsContract
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddress
import at.bitfire.davdroid.resource.LocalGroup
import at.bitfire.davdroid.syncadapter.ContactsSyncManager
import at.bitfire.davdroid.syncadapter.ContactsSyncManager.Companion.disjunct
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Contact
import java.io.FileNotFoundException

class VCard4Strategy(val syncManager: ContactsSyncManager): ContactGroupStrategy {

    private val localCollection = syncManager.localCollection

    override fun prepare() {
        // we want to get groups as entries (group contacts) from the local collection
        syncManager.localCollection.includeGroups = true
    }

    override fun beforeUploadDirty() {
        // mark groups with changed members as dirty
        val batch = BatchOperation(localCollection.provider!!)
        for (contact in localCollection.findDirtyContacts())
            try {
                Logger.log.fine("Looking for changed group memberships of contact ${contact.fileName}")
                val cachedGroups = contact.getCachedGroupMemberships()
                val currentGroups = contact.getGroupMemberships()
                for (groupID in cachedGroups disjunct currentGroups) {
                    Logger.log.fine("Marking group as dirty: $groupID")
                    batch.enqueue(BatchOperation.CpoBuilder
                            .newUpdate(localCollection.syncAdapterURI(ContentUris.withAppendedId(ContactsContract.Groups.CONTENT_URI, groupID)))
                            .withValue(ContactsContract.Groups.DIRTY, 1))
                }
            } catch(e: FileNotFoundException) {
            }
        batch.commit()
    }

    override fun beforeGenerateUpload(local: LocalAddress) {
    }

    override fun verifyContactBeforeSaving(contact: Contact) {
    }

    override fun afterSavingContact(local: LocalAddress) {
    }

    override fun postProcess() {
        Logger.log.info("Assigning memberships of downloaded contact groups")
        LocalGroup.applyPendingMemberships(localCollection)
    }

}