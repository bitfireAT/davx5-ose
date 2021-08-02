package at.bitfire.davdroid.resource.datarow

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.datarow.DataRowHandler

class GroupMembershipHandler(val localContact: LocalContact): DataRowHandler() {

    override fun forMimeType() = GroupMembership.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        localContact.groupMemberships += values.getAsLong(GroupMembership.GROUP_ROW_ID)
    }

}