/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation

class StructuredNameBuilder(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean)
    : DataRowBuilder(Factory.mimeType(), dataRowUri, rawContactId, contact, readOnly) {

    override fun build(): List<BatchOperation.CpoBuilder> {
        val hasStructuredComponents =
            contact.prefix != null ||
            contact.givenName != null || contact.middleName != null || contact.familyName != null ||
            contact.suffix != null ||
            contact.phoneticGivenName != null || contact.phoneticMiddleName != null || contact.phoneticFamilyName != null

        // no structured name info
        val displayName = contact.displayName
        if (displayName == null && !hasStructuredComponents)
            return emptyList()

        // only displayName and it's equivalent to one of the values in ContactWriter.addFormattedName →
        // don't create structured name row because it wouldn't add information but split the organization into given/family name
        if (displayName != null && !hasStructuredComponents && (
                contact.organization?.values?.contains(displayName) == true ||
                contact.organization?.values?.joinToString(" / ") == displayName ||
                contact.nickName?.property?.values?.contains(displayName) == true ||
                contact.emails.any { it.property.value == displayName } ||
                contact.phoneNumbers.any { it.property.text == displayName } ||
                contact.uid == displayName
        ))
            return emptyList()

        return listOf(newDataRow().apply {
            withValue(StructuredName.DISPLAY_NAME, displayName)
            withValue(StructuredName.PREFIX, contact.prefix)
            withValue(StructuredName.GIVEN_NAME, contact.givenName)
            withValue(StructuredName.MIDDLE_NAME, contact.middleName)
            withValue(StructuredName.FAMILY_NAME, contact.familyName)
            withValue(StructuredName.SUFFIX, contact.suffix)
            withValue(StructuredName.PHONETIC_GIVEN_NAME, contact.phoneticGivenName)
            withValue(StructuredName.PHONETIC_MIDDLE_NAME, contact.phoneticMiddleName)
            withValue(StructuredName.PHONETIC_FAMILY_NAME, contact.phoneticFamilyName)
        })
    }


    object Factory: DataRowBuilder.Factory<StructuredNameBuilder> {
        override fun mimeType() = StructuredName.CONTENT_ITEM_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            StructuredNameBuilder(dataRowUri, rawContactId, contact, readOnly)
    }

}