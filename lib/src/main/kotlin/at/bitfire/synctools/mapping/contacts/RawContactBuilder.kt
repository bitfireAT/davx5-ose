/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts

import android.net.Uri
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
import at.bitfire.synctools.storage.contacts.ContactsBatchOperation

class RawContactBuilder {

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

    fun registerBuilderFactory(factory: DataRowBuilder.Factory<*>) {
        dataRowBuilderFactories += factory
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
