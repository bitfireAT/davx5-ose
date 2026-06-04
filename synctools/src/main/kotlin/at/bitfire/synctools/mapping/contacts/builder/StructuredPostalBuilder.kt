/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.util.trimToNull
import ezvcard.parameter.AddressType
import java.util.LinkedList

/**
 * Data row builder for structured addresses.
 */
class StructuredPostalBuilder(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean)
    : DataRowBuilder(Factory.mimeType(), dataRowUri, rawContactId, contact, readOnly) {

    override fun build(): List<BatchOperation.CpoBuilder> {
        val result = LinkedList<BatchOperation.CpoBuilder>()
        for (labeledAddress in contact.addresses) {
            val address = labeledAddress.property

            /* Generate the formatted address (the one contacts app show in the preview) in European format.
             *
             * If we wouldn't do that, the content provider would format it US-EN or JP style:
             * https://android.googlesource.com/platform/packages/providers/ContactsProvider.git/+/refs/heads/android13-release/src/com/android/providers/contacts/PostalSplitter.java#84
             *
             * Could be localized here (but it's still only for viewing the contact on Android and won't be put into a vCard). */
            var formattedAddress = address.label
            if (formattedAddress.isNullOrBlank()) {
                val lines = LinkedList<String>()
                for (street in address.streetAddresses.filterNot { s -> s.isNullOrBlank() })
                    lines += street
                for (poBox in address.poBoxes.filterNot { s -> s.isNullOrBlank() })
                    lines += poBox
                for (extended in address.extendedAddresses.filterNot { s -> s.isNullOrBlank() })
                    lines += extended

                val postalAndCity = LinkedList<String>()
                if (address.postalCode.trimToNull() != null)
                    postalAndCity += address.postalCodes.joinToString(" / ")
                if (address.locality.trimToNull() != null)
                    postalAndCity += address.localities.joinToString(" / ")
                if (postalAndCity.isNotEmpty())
                    lines += postalAndCity.joinToString(" ")

                if (address.country.trimToNull() != null) {
                    val line = StringBuilder(address.countries.joinToString(" / "))
                    if (!address.region.isNullOrBlank()) {
                        val regions = address.regions.joinToString(" / ")
                        line.append(" ($regions)")
                    }
                    lines += line.toString()
                } else
                    if (!address.region.isNullOrBlank())
                        lines += address.regions.joinToString(" / ")

                formattedAddress = lines.joinToString("\n")
            }

            val types = address.types
            val typeCode: Int
            var typeLabel: String? = null
            if (labeledAddress.label != null) {
                typeCode = StructuredPostal.TYPE_CUSTOM
                typeLabel = labeledAddress.label
            } else
                typeCode = when {
                    types.contains(AddressType.HOME) -> StructuredPostal.TYPE_HOME
                    types.contains(AddressType.WORK) -> StructuredPostal.TYPE_WORK
                    else -> StructuredPostal.TYPE_OTHER
                }

            result += newDataRow()
                    .withValue(StructuredPostal.FORMATTED_ADDRESS, formattedAddress)
                    .withValue(StructuredPostal.TYPE, typeCode)
                    .withValue(StructuredPostal.LABEL, typeLabel)
                    .withValue(StructuredPostal.STREET, address.streetAddresses.joinToString("\n"))
                    .withValue(StructuredPostal.POBOX, address.poBoxes.joinToString("\n"))
                    .withValue(StructuredPostal.NEIGHBORHOOD, address.extendedAddresses.joinToString("\n"))
                    .withValue(StructuredPostal.CITY, address.localities.joinToString("\n"))
                    .withValue(StructuredPostal.REGION, address.regions.joinToString("\n"))
                    .withValue(StructuredPostal.POSTCODE, address.postalCodes.joinToString("\n"))
                    .withValue(StructuredPostal.COUNTRY, address.countries.joinToString("\n"))
        }
        return result
    }


    object Factory: DataRowBuilder.Factory<StructuredPostalBuilder> {
        override fun mimeType() = StructuredPostal.CONTENT_ITEM_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            StructuredPostalBuilder(dataRowUri, rawContactId, contact, readOnly)
    }

}