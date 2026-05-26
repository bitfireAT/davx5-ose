/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import ezvcard.parameter.ImppType
import ezvcard.property.Impp

object SipAddressHandler: DataRowHandler() {

    override fun forMimeType() = SipAddress.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)
        val sip = values.getAsString(SipAddress.SIP_ADDRESS) ?: return

        try {
            val impp = Impp("sip:$sip")
            val labeledImpp = LabeledProperty(impp)

            when (values.getAsInteger(SipAddress.TYPE)) {
                SipAddress.TYPE_HOME ->
                    impp.types += ImppType.HOME
                SipAddress.TYPE_WORK ->
                    impp.types += ImppType.WORK
                SipAddress.TYPE_CUSTOM ->
                    values.getAsString(SipAddress.LABEL)?.let {
                        labeledImpp.label = it
                    }
            }
            contact.impps.add(labeledImpp)
        } catch(e: IllegalArgumentException) {
            logger.warning("Ignoring invalid locally stored SIP address")
        }
   }

}