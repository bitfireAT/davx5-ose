package at.bitfire.davdroid.resource.datarow

import android.content.ContentValues
import at.bitfire.davdroid.model.UnknownProperties
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.datarow.DataRowHandler

object UnknownPropertiesHandler: DataRowHandler() {

    override fun forMimeType() = UnknownProperties.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        contact.unknownProperties = values.getAsString(UnknownProperties.UNKNOWN_PROPERTIES)
    }

}