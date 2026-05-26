/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.AndroidContact
import at.bitfire.synctools.storage.contacts.CachedGroupMembershipContract
import at.bitfire.synctools.vcard.GroupMethod

class CachedGroupMembershipHandler(
    val androidContact: AndroidContact,
    val groupMethod: GroupMethod
) : DataRowHandler() {

    override fun forMimeType() = CachedGroupMembershipContract.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        if (groupMethod == GroupMethod.GROUP_VCARDS) {
            val groupId = values.getAsLong(CachedGroupMembershipContract.GROUP_ID) ?: return
            androidContact.cachedGroupMemberships += groupId
        } else
            logger.warning("Ignoring cached group membership for group method CATEGORIES")
    }

}
