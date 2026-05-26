/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.contacts.UnknownPropertyContract
import java.util.LinkedList

class UnknownPropertiesBuilder(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) :
    DataRowBuilder(Factory.mimeType(), dataRowUri, rawContactId, contact, readOnly) {

    override fun build(): List<BatchOperation.CpoBuilder> {
        val result = LinkedList<BatchOperation.CpoBuilder>()
        contact.unknownProperties?.let { unknownProperties ->
            result += newDataRow().withValue(UnknownPropertyContract.UNKNOWN_PROPERTIES, unknownProperties)
        }
        return result
    }


    object Factory : DataRowBuilder.Factory<UnknownPropertiesBuilder> {
        override fun mimeType() = UnknownPropertyContract.CONTENT_ITEM_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            UnknownPropertiesBuilder(dataRowUri, rawContactId, contact, readOnly)
    }

}
