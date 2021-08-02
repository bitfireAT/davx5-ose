package at.bitfire.davdroid.resource.datarow

import android.content.ContentValues
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.datarow.DataRowHandler

class CachedGroupMembershipHandler(val localContact: LocalContact): DataRowHandler() {

    override fun forMimeType() = CachedGroupMembership.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        localContact.cachedGroupMemberships += values.getAsLong(CachedGroupMembership.GROUP_ID)
    }

}