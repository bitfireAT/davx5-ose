/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.RawContacts.getContactLookupUri
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.contacts.AddressContract.RawContactColumns
import at.bitfire.synctools.storage.contacts.AndroidContact
import com.google.common.base.MoreObjects
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class LocalContact : AndroidContact, LocalAddress {

    val localAddressBook: LocalAddressBook

    override val scheduleTag: String?
        get() = null


    constructor(localAddressBook: LocalAddressBook, values: ContentValues) : super(localAddressBook.ab, values) {
        this.localAddressBook = localAddressBook
    }

    constructor(localAddressBook: LocalAddressBook, contact: Contact, fileName: String?, eTag: String?, flags: Int) : super(
        localAddressBook.ab,
        contact,
        fileName,
        eTag,
        flags
    ) {
        this.localAddressBook = localAddressBook
    }

    /**
     * Clears cached contact (that is used by [getContact]) so that the next call of [getContact]
     * will query the content provider again.
     */
    fun clearCachedContact() {
        setContact(null)
    }

    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            throw IllegalArgumentException("Contacts must not have a Schedule-Tag")

        val values = ContentValues(4)
        if (fileName.isPresent)
            values.put(RawContactColumns.FILENAME, fileName.get())
        values.put(RawContactColumns.ETAG, eTag)
        values.put(RawContacts.DIRTY, 0)

        // Android 7 workaround
        localAddressBook.dirtyVerifier.getOrNull()?.setHashCodeColumn(this, values)

        addressBook.provider.update(rawContactSyncURI(), values, null, null)

        if (fileName.isPresent)
            this.fileName = fileName.get()
        this.eTag = eTag
    }

    fun resetDirty() {
        val values = contentValuesOf(RawContacts.DIRTY to 0)
        addressBook.provider.update(rawContactSyncURI(), values, null, null)
    }

    override fun update(data: Contact, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int) {
        this.fileName = fileName
        this.eTag = eTag
        this.flags = flags

        // processes this.{fileName, eTag, flags} and resets DIRTY flag
        update(data)
    }

    override fun updateFlags(flags: Int) {
        val values = contentValuesOf(RawContactColumns.FLAGS to flags)
        addressBook.provider.update(rawContactSyncURI(), values, null, null)

        this.flags = flags
    }

    override fun updateSequence(sequence: Int) = throw NotImplementedError()

    override fun updateUid(uid: String) {
        val values = contentValuesOf(RawContactColumns.UID to uid)
        addressBook.provider.update(rawContactSyncURI(), values, null, null)
    }

    override fun deleteLocal() {
        delete()
    }

    override fun resetDeleted() {
        val values = contentValuesOf(ContactsContract.Groups.DELETED to 0)
        addressBook.provider.update(rawContactSyncURI(), values, null, null)
    }

    override fun getDebugSummary() =
        MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("fileName", fileName)
            .add("eTag", eTag)
            .add("flags", flags)
            /*.add("contact",
                try {
                    // too dangerous, may contain unknown properties and cause another OOM
                    Ascii.truncate(getContact().toString(), 1000, "…")
                } catch (e: Exception) {
                    e
                }
            )*/
            .toString()

    override fun getViewUri(context: Context): Uri? =
        id?.let { idNotNull ->
            getContactLookupUri(
                context.contentResolver,
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, idNotNull)
            )
        }

}
