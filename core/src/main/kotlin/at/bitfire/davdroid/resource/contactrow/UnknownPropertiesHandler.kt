/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.contactrow

import android.content.ContentValues
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.handler.DataRowHandler

object UnknownPropertiesHandler: DataRowHandler() {

    override fun forMimeType() = UnknownProperties.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        contact.unknownProperties = values.getAsString(UnknownProperties.UNKNOWN_PROPERTIES)
    }

}