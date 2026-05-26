/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Im
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation
import ezvcard.parameter.ImppType
import java.util.LinkedList

class ImBuilder(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean)
    : DataRowBuilder(Factory.mimeType(), dataRowUri, rawContactId, contact, readOnly) {

    override fun build(): List<BatchOperation.CpoBuilder> {
        val result = LinkedList<BatchOperation.CpoBuilder>()
        for (labeledIm in contact.impps) {
            val impp = labeledIm.property

            val protocol = impp.protocol
            if (protocol == null) {
                logger.warning("Ignoring IM address without protocol")
                continue

            } else if (protocol == "sip")
                // IMPP:sip:…  is handled by SipAddressBuilder
                continue

            var typeCode: Int = Im.TYPE_OTHER
            var typeLabel: String? = null
            if (labeledIm.label != null) {
                typeCode = Im.TYPE_CUSTOM
                typeLabel = labeledIm.label
            } else {
                for (type in impp.types)
                    when (type) {
                        ImppType.HOME,
                        ImppType.PERSONAL -> typeCode = Im.TYPE_HOME
                        ImppType.WORK,
                        ImppType.BUSINESS -> typeCode = Im.TYPE_WORK
                    }
            }

            // save as IM address
            result += newDataRow()
                .withValue(Im.DATA, impp.handle)
                .withValue(Im.TYPE, typeCode)
                .withValue(Im.LABEL, typeLabel)
                .withValue(Im.PROTOCOL, Im.PROTOCOL_CUSTOM)
                .withValue(Im.CUSTOM_PROTOCOL, protocol)
        }
        return result
    }


    object Factory: DataRowBuilder.Factory<ImBuilder> {
        override fun mimeType() = Im.CONTENT_ITEM_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            ImBuilder(dataRowUri, rawContactId, contact, readOnly)
    }

}