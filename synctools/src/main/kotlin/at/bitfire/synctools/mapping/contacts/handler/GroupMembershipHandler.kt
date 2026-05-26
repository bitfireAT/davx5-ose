/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.AndroidContact
import at.bitfire.synctools.util.Utils.trimToNull
import at.bitfire.synctools.vcard.GroupMethod
import java.io.FileNotFoundException

class GroupMembershipHandler(
    val androidContact: AndroidContact,
    val groupMethod: GroupMethod
) : DataRowHandler() {

    override fun forMimeType() = GroupMembership.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        val groupId = values.getAsLong(GroupMembership.GROUP_ROW_ID) ?: return
        androidContact.groupMemberships += groupId

        if (groupMethod == GroupMethod.CATEGORIES) {
            try {
                val group = androidContact.addressBook.findGroupById(groupId)
                group.getContact().displayName.trimToNull()?.let { groupName ->
                    logger.fine("Adding membership in group $groupName as category")
                    contact.categories.add(groupName)
                }
            } catch (ignored: FileNotFoundException) {
                logger.warning("Contact is member in group $groupId which doesn't exist anymore")
            }
        }
    }

}
