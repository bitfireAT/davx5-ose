/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource.contactrow

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import at.bitfire.vcard4android.contactrow.DataRowBuilder
import java.util.*

class GroupMembershipBuilder(dataRowUri: Uri, rawContactId: Long?, contact: Contact, val addressBook: LocalAddressBook, readOnly: Boolean)
    : DataRowBuilder(Factory.MIME_TYPE, dataRowUri, rawContactId, contact, readOnly) {

    override fun build(): List<BatchOperation.CpoBuilder> {
        val result = LinkedList<BatchOperation.CpoBuilder>()

        if (addressBook.groupMethod == GroupMethod.CATEGORIES)
            for (category in contact.categories)
                result += newDataRow().withValue(GroupMembership.GROUP_ROW_ID, addressBook.findOrCreateGroup(category))
        else {
            // GroupMethod.GROUP_VCARDS -> memberships are handled by LocalGroups (and not by the members = LocalContacts, which we are processing here)
            // TODO: CATEGORIES <-> unknown properties
        }

        return result
    }


    class Factory(val addressBook: LocalAddressBook): DataRowBuilder.Factory<GroupMembershipBuilder> {
        companion object {
            const val MIME_TYPE = GroupMembership.CONTENT_ITEM_TYPE
        }
        override fun mimeType() = MIME_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            GroupMembershipBuilder(dataRowUri, rawContactId, contact, addressBook, readOnly)
    }

}