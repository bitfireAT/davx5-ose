/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.AddressContract

object UnknownPropertiesHandler : DataRowHandler() {

    override fun forMimeType() = AddressContract.UnknownProperty.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        contact.unknownProperties = values.getAsString(AddressContract.UnknownProperty.UNKNOWN_PROPERTIES)
    }

}
