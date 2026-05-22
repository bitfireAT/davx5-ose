/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts

import android.content.ContentProviderClient
import android.content.ContentValues
import android.provider.ContactsContract.RawContacts
import at.bitfire.synctools.mapping.contacts.handler.DataRowHandler
import at.bitfire.synctools.mapping.contacts.handler.EmailHandler
import at.bitfire.synctools.mapping.contacts.handler.EventHandler
import at.bitfire.synctools.mapping.contacts.handler.ImHandler
import at.bitfire.synctools.mapping.contacts.handler.NicknameHandler
import at.bitfire.synctools.mapping.contacts.handler.NoteHandler
import at.bitfire.synctools.mapping.contacts.handler.OrganizationHandler
import at.bitfire.synctools.mapping.contacts.handler.PhoneHandler
import at.bitfire.synctools.mapping.contacts.handler.PhotoHandler
import at.bitfire.synctools.mapping.contacts.handler.RelationHandler
import at.bitfire.synctools.mapping.contacts.handler.SipAddressHandler
import at.bitfire.synctools.mapping.contacts.handler.StructuredNameHandler
import at.bitfire.synctools.mapping.contacts.handler.StructuredPostalHandler
import at.bitfire.synctools.mapping.contacts.handler.WebsiteHandler
import at.bitfire.synctools.storage.contacts.AndroidContact
import java.util.logging.Level
import java.util.logging.Logger

class RawContactHandler(
    provider: ContentProviderClient
) {

    private val dataRowHandlers = mutableMapOf<String, MutableList<DataRowHandler>>()
    private val defaultDataRowHandlers = arrayOf(
        EmailHandler,
        EventHandler,
        ImHandler,
        NicknameHandler,
        NoteHandler,
        OrganizationHandler,
        PhoneHandler,
        PhotoHandler(provider),
        RelationHandler,
        SipAddressHandler,
        StructuredNameHandler,
        StructuredPostalHandler,
        WebsiteHandler
    )

    init {
        for (handler in defaultDataRowHandlers)
            registerHandler(handler)
    }

    fun registerHandler(handler: DataRowHandler) {
        val mimeType = handler.forMimeType()
        val handlers = dataRowHandlers[mimeType] ?: run {
            val newList = mutableListOf<DataRowHandler>()
            dataRowHandlers[mimeType] = newList
            newList
        }

        handlers += handler
    }

    fun handleRawContact(values: ContentValues, contact: Contact) {
        contact.uid = values.getAsString(AndroidContact.COLUMN_UID)
    }

    fun handleDataRow(values: ContentValues, contact: Contact) {
        val mimeType = values.getAsString(RawContacts.Data.MIMETYPE)

        val handlers = dataRowHandlers[mimeType].orEmpty()
        if (handlers.isNotEmpty())
            for (handler in handlers)
                handler.handle(values, contact)
        else {
            val logger = Logger.getLogger(javaClass.name)
            logger.log(Level.WARNING, "No registered handler for $mimeType", values)
        }
    }

}
