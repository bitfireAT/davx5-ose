/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.vcard.property.CustomType
import java.util.LinkedList

class NicknameBuilder(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean)
    : DataRowBuilder(Factory.mimeType(), dataRowUri, rawContactId, contact, readOnly) {

    override fun build(): List<BatchOperation.CpoBuilder> {
        val labeledNick = contact.nickName ?: return emptyList()
        val result = LinkedList<BatchOperation.CpoBuilder>()

        val label = labeledNick.label
        val nick = labeledNick.property
        for (nickValue in labeledNick.property.values) {
            if (nickValue.isNullOrBlank())
                continue

            val typeCode: Int
            var typeLabel: String? = null

            if (label != null) {
                typeCode = CommonDataKinds.Nickname.TYPE_CUSTOM
                typeLabel = label
            } else {
                val type = nick.type?.lowercase()
                typeCode = when (type) {
                    CustomType.Nickname.INITIALS -> CommonDataKinds.Nickname.TYPE_INITIALS
                    CustomType.Nickname.MAIDEN_NAME -> CommonDataKinds.Nickname.TYPE_MAIDEN_NAME
                    CustomType.Nickname.SHORT_NAME -> CommonDataKinds.Nickname.TYPE_SHORT_NAME
                    null -> CommonDataKinds.Nickname.TYPE_DEFAULT
                    else -> CommonDataKinds.Nickname.TYPE_OTHER_NAME
                }
            }

            result += newDataRow()
                .withValue(CommonDataKinds.Nickname.NAME, nickValue)
                .withValue(CommonDataKinds.Nickname.TYPE, typeCode)
                .withValue(CommonDataKinds.Nickname.LABEL, typeLabel)
        }
        return result
    }


    object Factory: DataRowBuilder.Factory<NicknameBuilder> {
        override fun mimeType() = CommonDataKinds.Nickname.CONTENT_ITEM_TYPE
        override fun newInstance(dataRowUri: Uri, rawContactId: Long?, contact: Contact, readOnly: Boolean) =
            NicknameBuilder(dataRowUri, rawContactId, contact, readOnly)
    }

}