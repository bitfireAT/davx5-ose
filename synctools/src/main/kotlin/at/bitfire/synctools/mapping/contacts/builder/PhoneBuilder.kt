/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.vcard.property.CustomType
import ezvcard.parameter.TelephoneType
import java.util.LinkedList
import java.util.logging.Level

class PhoneBuilder(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean)
    : DataRowBuilder(Factory.mimeType(), dataRowUri, rawContactId, contact, readOnly) {

    override fun build(): List<BatchOperation.CpoBuilder> {
        val result = LinkedList<BatchOperation.CpoBuilder>()
        for (phoneNumber in contact.phoneNumbers) {
            val tel = phoneNumber.property

            // TEL can have either a TEXT (default for vCard 3 compatibility) or an URI value.
            val uri = tel.uri
            val number: String?
            if (uri != null) {
                val baseNumber = uri.number
                val ext = uri.extension
                number =
                    if (!baseNumber.isNullOrBlank() && !ext.isNullOrBlank() &&
                            !baseNumber.contains(PhoneNumberUtils.WAIT) && !baseNumber.contains(PhoneNumberUtils.PAUSE))
                        "$baseNumber${PhoneNumberUtils.WAIT}$ext"
                    else
                        baseNumber
            } else {
                number = tel.text
            }

            // Skip empty numbers
            if (number.isNullOrBlank())
                continue

            val types = tel.types

            // preferred number?
            var pref: Int? = null
            try {
                pref = tel.pref
            } catch(e: IllegalStateException) {
                logger.log(Level.FINER, "Can't understand phone number PREF", e)
            }
            var isPrimary = pref != null
            if (types.contains(TelephoneType.PREF)) {
                isPrimary = true
                types -= TelephoneType.PREF
            }

            var typeCode: Int = Phone.TYPE_OTHER
            var typeLabel: String? = null
            if (phoneNumber.label != null) {
                typeCode = Phone.TYPE_CUSTOM
                typeLabel = phoneNumber.label
            } else {
                when {
                    // 1 Android type <-> 2 vCard types: fax, cell, pager
                    types.contains(TelephoneType.CELL) ->
                        typeCode = if (types.contains(TelephoneType.WORK))
                            Phone.TYPE_WORK_MOBILE
                        else
                            Phone.TYPE_MOBILE
                    types.contains(TelephoneType.FAX) ->
                        typeCode = when {
                            types.contains(TelephoneType.HOME) -> Phone.TYPE_FAX_HOME
                            types.contains(TelephoneType.WORK) -> Phone.TYPE_FAX_WORK
                            else                               -> Phone.TYPE_OTHER_FAX
                        }
                    types.contains(TelephoneType.PAGER) ->
                        typeCode = if (types.contains(TelephoneType.WORK))
                            Phone.TYPE_WORK_PAGER
                        else
                            Phone.TYPE_PAGER

                    // types with 1:1 translation
                    types.contains(TelephoneType.HOME) ->
                        typeCode = Phone.TYPE_HOME
                    types.contains(TelephoneType.WORK) ->
                        typeCode = Phone.TYPE_WORK
                    types.contains(CustomType.Phone.CALLBACK) ->
                        typeCode = Phone.TYPE_CALLBACK
                    types.contains(TelephoneType.CAR) ->
                        typeCode = Phone.TYPE_CAR
                    types.contains(CustomType.Phone.COMPANY_MAIN) ->
                        typeCode = Phone.TYPE_COMPANY_MAIN
                    types.contains(TelephoneType.ISDN) ->
                        typeCode = Phone.TYPE_ISDN
                    types.contains(CustomType.Phone.RADIO) ->
                        typeCode = Phone.TYPE_RADIO
                    types.contains(CustomType.Phone.ASSISTANT) ->
                        typeCode = Phone.TYPE_ASSISTANT
                    types.contains(CustomType.Phone.MMS) ->
                        typeCode = Phone.TYPE_MMS
                }
            }

            result += newDataRow()
                .withValue(Phone.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, number)
                .withValue(Phone.TYPE, typeCode)
                .withValue(Phone.LABEL, typeLabel)
                .withValue(Phone.IS_PRIMARY, if (isPrimary) 1 else 0)
                .withValue(Phone.IS_SUPER_PRIMARY, if (isPrimary) 1 else 0)
        }
        return result
    }


    object Factory: DataRowBuilder.Factory<PhoneBuilder> {
        override fun mimeType() = Phone.CONTENT_ITEM_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            PhoneBuilder(dataRowUri, rawContactId, contact, readOnly)
    }

}
