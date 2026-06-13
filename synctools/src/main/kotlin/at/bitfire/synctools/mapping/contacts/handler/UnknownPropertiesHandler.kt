/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.ContactContract

object UnknownPropertiesHandler : DataRowHandler() {

    override fun forMimeType() = ContactContract.UnknownProperty.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        contact.unknownProperties = values.getAsString(ContactContract.UnknownProperty.UNKNOWN_PROPERTIES)
    }

}
