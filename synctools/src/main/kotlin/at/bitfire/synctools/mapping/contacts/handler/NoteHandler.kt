/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Note
import at.bitfire.synctools.mapping.contacts.Contact

object NoteHandler: DataRowHandler() {

    override fun forMimeType() = Note.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)
        contact.note = values.getAsString(Note.NOTE)
    }

}