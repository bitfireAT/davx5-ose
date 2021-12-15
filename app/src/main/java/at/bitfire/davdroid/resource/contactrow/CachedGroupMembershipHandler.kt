/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource.contactrow

import android.content.ContentValues
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import at.bitfire.vcard4android.contactrow.DataRowHandler

class CachedGroupMembershipHandler(val localContact: LocalContact): DataRowHandler() {

    override fun forMimeType() = CachedGroupMembership.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        if (localContact.addressBook.groupMethod == GroupMethod.GROUP_VCARDS)
            localContact.cachedGroupMemberships += values.getAsLong(CachedGroupMembership.GROUP_ID)
        else
            Logger.log.warning("Ignoring cached group membership for group method CATEGORIES")
    }

}