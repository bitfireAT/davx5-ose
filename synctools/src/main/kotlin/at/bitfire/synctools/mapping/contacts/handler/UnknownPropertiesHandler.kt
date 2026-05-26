/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.UnknownPropertyContract

object UnknownPropertiesHandler : DataRowHandler() {

    override fun forMimeType() = UnknownPropertyContract.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        contact.unknownProperties = values.getAsString(UnknownPropertyContract.UNKNOWN_PROPERTIES)
    }

}
