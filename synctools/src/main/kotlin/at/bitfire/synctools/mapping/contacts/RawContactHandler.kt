/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts

import android.content.ContentValues
import android.provider.ContactsContract.RawContacts
import at.bitfire.synctools.mapping.contacts.handler.CachedGroupMembershipHandler
import at.bitfire.synctools.mapping.contacts.handler.DataRowHandler
import at.bitfire.synctools.mapping.contacts.handler.EmailHandler
import at.bitfire.synctools.mapping.contacts.handler.EventHandler
import at.bitfire.synctools.mapping.contacts.handler.GroupMembershipHandler
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
import at.bitfire.synctools.mapping.contacts.handler.UnknownPropertiesHandler
import at.bitfire.synctools.mapping.contacts.handler.WebsiteHandler
import at.bitfire.synctools.storage.contacts.AddressContract
import at.bitfire.synctools.storage.contacts.AndroidContact
import java.util.logging.Logger

class RawContactHandler(
    androidContact: AndroidContact
) {

    private val dataRowHandlers = mutableMapOf<String, MutableList<DataRowHandler>>()
    private val defaultDataRowHandlers = arrayOf(
        CachedGroupMembershipHandler(androidContact, androidContact.addressBook.groupMethod),
        EmailHandler,
        EventHandler,
        GroupMembershipHandler(androidContact, androidContact.addressBook.groupMethod),
        ImHandler,
        NicknameHandler,
        NoteHandler,
        OrganizationHandler,
        PhoneHandler,
        PhotoHandler(androidContact.addressBook.provider),
        RelationHandler,
        SipAddressHandler,
        StructuredNameHandler,
        StructuredPostalHandler,
        UnknownPropertiesHandler,
        WebsiteHandler
    )

    init {
        for (handler in defaultDataRowHandlers)
            registerHandler(handler)
    }

    private fun registerHandler(handler: DataRowHandler) {
        val mimeType = handler.forMimeType()
        val handlers = dataRowHandlers[mimeType] ?: run {
            val newList = mutableListOf<DataRowHandler>()
            dataRowHandlers[mimeType] = newList
            newList
        }

        handlers += handler
    }

    fun handleRawContact(values: ContentValues, contact: Contact) {
        contact.uid = values.getAsString(AddressContract.RawContactColumns.UID)
    }

    fun handleDataRow(values: ContentValues, contact: Contact) {
        val mimeType = values.getAsString(RawContacts.Data.MIMETYPE)

        val handlers = dataRowHandlers[mimeType].orEmpty()
        if (handlers.isNotEmpty())
            for (handler in handlers)
                handler.handle(values, contact)
        else {
            val logger = Logger.getLogger(javaClass.name)
            logger.warning("No registered handler for $mimeType: $values")
        }
    }

}
