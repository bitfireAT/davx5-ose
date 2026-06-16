/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.contacts.AndroidAddressBook
import at.bitfire.synctools.vcard.GroupMethod
import java.util.LinkedList

class GroupMembershipBuilder(
    dataRowUri: Uri,
    rawContactId: Long?,
    contact: Contact,
    val addressBook: AndroidAddressBook,
    val groupMethod: GroupMethod,
    readOnly: Boolean
) : DataRowBuilder(Factory.MIME_TYPE, dataRowUri, rawContactId, contact, readOnly) {

    override fun build(): List<BatchOperation.CpoBuilder> {
        val result = LinkedList<BatchOperation.CpoBuilder>()

        if (groupMethod == GroupMethod.CATEGORIES)
            for (category in contact.categories)
                result += newDataRow().withValue(GroupMembership.GROUP_ROW_ID, addressBook.findOrCreateGroup(category))
        else {
            // GroupMethod.GROUP_VCARDS -> memberships are handled by AndroidGroups (and not by the members = AndroidContacts, which we are processing here)
            // TODO: CATEGORIES <-> unknown properties
        }

        return result
    }


    class Factory(val addressBook: AndroidAddressBook, val groupMethod: GroupMethod) : DataRowBuilder.Factory<GroupMembershipBuilder> {
        companion object {
            const val MIME_TYPE = GroupMembership.CONTENT_ITEM_TYPE
        }

        override fun mimeType() = MIME_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            GroupMembershipBuilder(dataRowUri, rawContactId, contact, addressBook, groupMethod, readOnly)
    }

}
