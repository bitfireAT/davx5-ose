/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.contacts

import android.provider.ContactsContract.RawContacts.Data

/**
 * Represents a "cached group membership" row. Cached group memberships exist only
 * for one reason, which _only_ applies to the vCard4 (KIND/MEMBER) group method:
 *
 * Every group has its list of members. When a contact's group memberships are changed,
 * the contact is automatically set to dirty, but the group itself is not!
 *
 * So we keep a copy of all group membership rows as "cached memberships". At the
 * beginning of every sync, the group memberships of every contact are compared with
 * its cached group memberships. If they differ, the respective contact group
 * is set to dirty (because its memberships have changed).
 *
 * Cached group memberships must not be used for anything else that detecting dirty groups.
 */
object CachedGroupMembership {

    const val CONTENT_ITEM_TYPE = "x.davdroid/cached-group-membership"

    const val MIMETYPE = Data.MIMETYPE
    const val RAW_CONTACT_ID = Data.RAW_CONTACT_ID
    const val GROUP_ID = Data.DATA1

}
