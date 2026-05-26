/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import at.bitfire.synctools.mapping.contacts.Contact

object StructuredNameHandler: DataRowHandler() {

    override fun forMimeType() = StructuredName.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        contact.displayName = values.getAsString(StructuredName.DISPLAY_NAME)

        contact.prefix = values.getAsString(StructuredName.PREFIX)
        contact.givenName = values.getAsString(StructuredName.GIVEN_NAME)
        contact.middleName = values.getAsString(StructuredName.MIDDLE_NAME)
        contact.familyName = values.getAsString(StructuredName.FAMILY_NAME)
        contact.suffix = values.getAsString(StructuredName.SUFFIX)

        contact.phoneticGivenName = values.getAsString(StructuredName.PHONETIC_GIVEN_NAME)
        contact.phoneticMiddleName = values.getAsString(StructuredName.PHONETIC_MIDDLE_NAME)
        contact.phoneticFamilyName = values.getAsString(StructuredName.PHONETIC_FAMILY_NAME)
    }

}