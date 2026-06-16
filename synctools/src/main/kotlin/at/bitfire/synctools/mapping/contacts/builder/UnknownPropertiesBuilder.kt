/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.contacts.AddressContract
import java.util.LinkedList

class UnknownPropertiesBuilder(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) :
    DataRowBuilder(Factory.mimeType(), dataRowUri, rawContactId, contact, readOnly) {

    override fun build(): List<BatchOperation.CpoBuilder> {
        val result = LinkedList<BatchOperation.CpoBuilder>()
        contact.unknownProperties?.let { unknownProperties ->
            result += newDataRow().withValue(AddressContract.UnknownProperty.UNKNOWN_PROPERTIES, unknownProperties)
        }
        return result
    }


    object Factory : DataRowBuilder.Factory<UnknownPropertiesBuilder> {
        override fun mimeType() = AddressContract.UnknownProperty.CONTENT_ITEM_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            UnknownPropertiesBuilder(dataRowUri, rawContactId, contact, readOnly)
    }

}
