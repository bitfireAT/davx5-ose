/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import ezvcard.parameter.AddressType
import ezvcard.property.Address

object StructuredPostalHandler: DataRowHandler() {

    override fun forMimeType() = StructuredPostal.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        val address = Address()
        val labeledAddress = LabeledProperty(address)

        /* Sep 2022: We don't set the vCard LABEL anymore. Reasons:
         *
         * 1. It can't be entered by the user anyway because no contacts app has a separate field for "formatted address"
         *    [https://www.davx5.com/faq/entering-structured-addresses], which is only used as read-only field to display an address.
         * 2. It confuses other CalDAV user agents which don't support LABEL (the majority). When such a client receives
         *    and retains the LABEL although the structured address is changed, there are two inconsistent addresses.
         *    [https://github.com/nextcloud/contacts/issues/1900]
         */
        //address.label = values.getAsString(StructuredPostal.FORMATTED_ADDRESS)

        when (values.getAsInteger(StructuredPostal.TYPE)) {
            StructuredPostal.TYPE_HOME ->
                address.types += AddressType.HOME
            StructuredPostal.TYPE_WORK ->
                address.types += AddressType.WORK
            StructuredPostal.TYPE_CUSTOM -> {
                values.getAsString(StructuredPostal.LABEL)?.let {
                    labeledAddress.label = it
                }
            }
        }
        values.getAsString(StructuredPostal.STREET)?.let { streets ->
            address.streetAddresses += streets.split('\n')
        }
        values.getAsString(StructuredPostal.POBOX)?.let { poBoxes ->
            address.poBoxes += poBoxes.split('\n')
        }
        values.getAsString(StructuredPostal.NEIGHBORHOOD)?.let { neighborhoods ->
            address.extendedAddresses += neighborhoods.split('\n')
        }
        values.getAsString(StructuredPostal.CITY)?.let { cities ->
            address.localities += cities.split('\n')
        }
        values.getAsString(StructuredPostal.REGION)?.let { regions ->
            address.regions += regions.split('\n')
        }
        values.getAsString(StructuredPostal.POSTCODE)?.let { postalCodes ->
            address.postalCodes += postalCodes.split('\n')
        }
        values.getAsString(StructuredPostal.COUNTRY)?.let { countries ->
            address.countries += countries.split('\n')
        }

        if (address.streetAddresses.isNotEmpty() ||
            address.poBoxes.isNotEmpty() ||
            address.extendedAddresses.isNotEmpty() ||
            address.localities.isNotEmpty() ||
            address.regions.isNotEmpty() ||
            address.postalCodes.isNotEmpty() ||
            address.countries.isNotEmpty())
            contact.addresses += labeledAddress
    }

}