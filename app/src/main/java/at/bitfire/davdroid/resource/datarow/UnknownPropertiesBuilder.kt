package at.bitfire.davdroid.resource.datarow

import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import at.bitfire.davdroid.model.UnknownProperties
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Constants
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.datarow.DataRowBuilder
import ezvcard.parameter.EmailType
import java.util.*
import java.util.logging.Level

class UnknownPropertiesBuilder(mimeType: String, dataRowUri: Uri, rawContactId: Long?, contact: Contact)
    : DataRowBuilder(mimeType, dataRowUri, rawContactId, contact) {

    override fun build(): List<BatchOperation.CpoBuilder> {
        val result = LinkedList<BatchOperation.CpoBuilder>()
        contact.unknownProperties?.let { unknownProperties ->
            result += newDataRow().withValue(UnknownProperties.UNKNOWN_PROPERTIES, unknownProperties)
        }
        return result
    }


    object Factory: DataRowBuilder.Factory<UnknownPropertiesBuilder> {
        override fun mimeType() = UnknownProperties.CONTENT_ITEM_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact) =
            UnknownPropertiesBuilder(mimeType(), dataRowUri, rawContactId, contact)
    }

}