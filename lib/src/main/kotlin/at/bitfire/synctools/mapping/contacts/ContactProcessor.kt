/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts

import android.content.ContentProviderClient
import android.content.ContentValues
import android.net.Uri
import android.provider.ContactsContract.RawContacts
import at.bitfire.synctools.mapping.contacts.builder.DataRowBuilder
import at.bitfire.synctools.mapping.contacts.builder.EmailBuilder
import at.bitfire.synctools.mapping.contacts.builder.EventBuilder
import at.bitfire.synctools.mapping.contacts.builder.ImBuilder
import at.bitfire.synctools.mapping.contacts.builder.NicknameBuilder
import at.bitfire.synctools.mapping.contacts.builder.NoteBuilder
import at.bitfire.synctools.mapping.contacts.builder.OrganizationBuilder
import at.bitfire.synctools.mapping.contacts.builder.PhoneBuilder
import at.bitfire.synctools.mapping.contacts.builder.PhotoBuilder
import at.bitfire.synctools.mapping.contacts.builder.RelationBuilder
import at.bitfire.synctools.mapping.contacts.builder.SipAddressBuilder
import at.bitfire.synctools.mapping.contacts.builder.StructuredNameBuilder
import at.bitfire.synctools.mapping.contacts.builder.StructuredPostalBuilder
import at.bitfire.synctools.mapping.contacts.builder.WebsiteBuilder
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
import at.bitfire.synctools.storage.contacts.ContactsBatchOperation
import java.util.logging.Level
import java.util.logging.Logger

class ContactProcessor(
    val provider: ContentProviderClient?
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

    private val dataRowBuilderFactories = mutableListOf<DataRowBuilder.Factory<*>>(
        EmailBuilder.Factory,
        EventBuilder.Factory,
        ImBuilder.Factory,
        NicknameBuilder.Factory,
        NoteBuilder.Factory,
        OrganizationBuilder.Factory,
        PhoneBuilder.Factory,
        PhotoBuilder.Factory,
        RelationBuilder.Factory,
        SipAddressBuilder.Factory,
        StructuredNameBuilder.Factory,
        StructuredPostalBuilder.Factory,
        WebsiteBuilder.Factory
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

    fun registerBuilderFactory(factory: DataRowBuilder.Factory<*>) {
        dataRowBuilderFactories += factory
    }


    fun handleRawContact(values: ContentValues, contact: Contact) {
        contact.uid = values.getAsString(at.bitfire.synctools.storage.contacts.AndroidContact.COLUMN_UID)
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


    fun insertDataRows(dataRowUri: Uri, rawContactId: Long?, contact: Contact, batch: ContactsBatchOperation, readOnly: Boolean) {
        for (factory in dataRowBuilderFactories) {
            val builder = factory.newInstance(dataRowUri, rawContactId, contact, readOnly)
            batch += builder.build()
        }
    }


    fun builderMimeTypes(): Set<String> {
        val mimeTypes = mutableSetOf<String>()
        for (factory in dataRowBuilderFactories)
            mimeTypes += factory.mimeType()
        return mimeTypes
    }

}
