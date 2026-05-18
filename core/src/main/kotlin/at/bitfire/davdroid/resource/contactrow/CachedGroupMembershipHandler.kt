/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.contactrow

import android.content.ContentValues
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.handler.DataRowHandler
import at.bitfire.synctools.storage.contacts.CachedGroupMembership
import at.bitfire.synctools.vcard.GroupMethod
import java.util.logging.Logger

class CachedGroupMembershipHandler(val localContact: LocalContact): DataRowHandler() {
    
    override fun forMimeType() = CachedGroupMembership.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        if (localContact.addressBook.groupMethod == GroupMethod.GROUP_VCARDS)
            localContact.cachedGroupMemberships += values.getAsLong(CachedGroupMembership.GROUP_ID)
        else
            Logger.getGlobal().warning("Ignoring cached group membership for group method CATEGORIES")
    }

}