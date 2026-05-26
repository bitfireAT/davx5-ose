/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation
import ezvcard.parameter.ImppType
import java.util.LinkedList

class SipAddressBuilder(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean)
    : DataRowBuilder(Factory.mimeType(), dataRowUri, rawContactId, contact, readOnly) {

    override fun build(): List<BatchOperation.CpoBuilder> {
        val result = LinkedList<BatchOperation.CpoBuilder>()
        for (labeledIm in contact.impps) {
            val impp = labeledIm.property

            val protocol = impp.protocol
            if (protocol != "sip")
                // other protocols are handled by ImBuilder
                continue

            var typeCode: Int = SipAddress.TYPE_OTHER
            var typeLabel: String? = null
            if (labeledIm.label != null) {
                typeCode = SipAddress.TYPE_CUSTOM
                typeLabel = labeledIm.label
            } else {
                for (type in impp.types)
                    when (type) {
                        ImppType.HOME,
                        ImppType.PERSONAL -> typeCode = SipAddress.TYPE_HOME
                        ImppType.WORK,
                        ImppType.BUSINESS -> typeCode = SipAddress.TYPE_WORK
                    }
            }

            // save as IM address
            result += newDataRow()
                .withValue(SipAddress.SIP_ADDRESS, impp.handle)
                .withValue(SipAddress.TYPE, typeCode)
                .withValue(SipAddress.LABEL, typeLabel)
        }
        return result
    }


    object Factory: DataRowBuilder.Factory<SipAddressBuilder> {
        override fun mimeType() = SipAddress.CONTENT_ITEM_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            SipAddressBuilder(dataRowUri, rawContactId, contact, readOnly)
    }

}