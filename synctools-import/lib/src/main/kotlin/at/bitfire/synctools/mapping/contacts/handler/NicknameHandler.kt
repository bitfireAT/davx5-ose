/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Nickname
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import at.bitfire.synctools.vcard.property.CustomType

object NicknameHandler: DataRowHandler() {

    override fun forMimeType() = Nickname.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        val name = values.getAsString(Nickname.NAME) ?: return
        val nick = ezvcard.property.Nickname()
        val labeledNick = LabeledProperty(nick)

        nick.values += name

        when (values.getAsInteger(Nickname.TYPE)) {
            Nickname.TYPE_MAIDEN_NAME ->
                nick.type = CustomType.Nickname.MAIDEN_NAME
            Nickname.TYPE_SHORT_NAME ->
                nick.type = CustomType.Nickname.SHORT_NAME
            Nickname.TYPE_INITIALS ->
                nick.type = CustomType.Nickname.INITIALS
            Nickname.TYPE_CUSTOM ->
                values.getAsString(Nickname.LABEL)?.let { labeledNick.label = it }
        }

        contact.nickName = labeledNick
    }

}