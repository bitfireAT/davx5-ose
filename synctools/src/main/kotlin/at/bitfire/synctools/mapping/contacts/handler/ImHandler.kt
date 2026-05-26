/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Im
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import ezvcard.parameter.ImppType
import ezvcard.property.Impp

object ImHandler: DataRowHandler() {

    override fun forMimeType() = Im.CONTENT_ITEM_TYPE

    @Suppress("DEPRECATION")
    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        val handle = values.getAsString(Im.DATA)
        if (handle == null) {
            logger.warning("Ignoring instant messenger record without handle")
            return
        }

        var impp: Impp? = null
        when (values.getAsInteger(Im.PROTOCOL)) {
            Im.PROTOCOL_AIM ->
                impp = Impp.aim(handle)
            Im.PROTOCOL_MSN ->
                impp = Impp.msn(handle)
            Im.PROTOCOL_YAHOO ->
                impp = Impp.yahoo(handle)
            Im.PROTOCOL_SKYPE ->
                impp = Impp.skype(handle)
            Im.PROTOCOL_QQ ->
                impp = Impp("qq", handle)
            Im.PROTOCOL_GOOGLE_TALK ->
                impp = Impp("google-talk", handle)
            Im.PROTOCOL_ICQ ->
                impp = Impp.icq(handle)
            Im.PROTOCOL_JABBER ->
                impp = Impp.xmpp(handle)
            Im.PROTOCOL_NETMEETING ->
                impp = Impp("netmeeting", handle)
            Im.PROTOCOL_CUSTOM ->
                try {
                    impp = Impp(protocolToUriScheme(values.getAsString(Im.CUSTOM_PROTOCOL)), handle)
                } catch(e: IllegalArgumentException) {
                    logger.warning("Messenger type/value can't be expressed as URI; ignoring")
                }
        }

        if (impp == null)
            return
        val labeledImpp = LabeledProperty(impp)

        when (values.getAsInteger(Im.TYPE)) {
            Im.TYPE_HOME ->
                impp.types += ImppType.HOME
            Im.TYPE_WORK ->
                impp.types += ImppType.WORK
            Im.TYPE_CUSTOM ->
                values.getAsString(Im.LABEL)?.let {
                    labeledImpp.label = it
                }
        }

        contact.impps += labeledImpp
    }

    fun protocolToUriScheme(s: String?) =
            // RFC 3986 3.1
            // scheme      = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
            // ALPHA       =  %x41-5A / %x61-7A   ; A-Z / a-z
                // DIGIT       =  %x30-39             ; 0-9
            s?.replace(Regex("^[^a-zA-Z]+"), "")?.replace(Regex("[^\\da-zA-Z+-.]"), "")?.lowercase()

}