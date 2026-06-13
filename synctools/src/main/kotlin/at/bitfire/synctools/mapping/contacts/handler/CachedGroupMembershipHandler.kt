/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.AddressContract
import at.bitfire.synctools.storage.contacts.AndroidContact
import at.bitfire.synctools.vcard.GroupMethod

class CachedGroupMembershipHandler(
    val androidContact: AndroidContact,
    val groupMethod: GroupMethod
) : DataRowHandler() {

    override fun forMimeType() = AddressContract.CachedGroupMembership.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        if (groupMethod == GroupMethod.GROUP_VCARDS) {
            val groupId = values.getAsLong(AddressContract.CachedGroupMembership.GROUP_ID) ?: return
            androidContact.cachedGroupMemberships += groupId
        } else
            logger.warning("Ignoring cached group membership for group method CATEGORIES")
    }

}
