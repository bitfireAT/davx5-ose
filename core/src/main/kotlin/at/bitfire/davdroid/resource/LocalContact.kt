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

class LocalContact(
    val localAddressBook: LocalAddressBook,
    val androidContact: AndroidContact
) : LocalAddress {

    private val provider get() = androidContact.addressBook.provider

    override val id: Long?
        get() = androidContact.id

    override val fileName: String?
        get() = androidContact.fileName

    override val eTag: String?
        get() = androidContact.eTag

    override val flags: Int
        get() = androidContact.flags

    override val scheduleTag: String?
        get() = null


    /**
     * Clears cached contact (that is used by [AndroidContact.getContact]) so that the next call of [AndroidContact.getContact]
     * will query the content provider again.
     */
    fun clearCachedContact() {
        androidContact.setContact(null)
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

        provider.update(androidContact.rawContactSyncURI(), values, null, null)

        if (fileName.isPresent)
            androidContact.fileName = fileName.get()
        androidContact.eTag = eTag
    }

    fun resetDirty() {
        val values = contentValuesOf(RawContacts.DIRTY to 0)
        provider.update(androidContact.rawContactSyncURI(), values, null, null)
    }

    override fun update(data: Contact, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int) {
        androidContact.fileName = fileName
        androidContact.eTag = eTag
        androidContact.flags = flags

        // processes androidContact.{fileName, eTag, flags} and resets DIRTY flag
        androidContact.update(data)
    }

    override fun updateFlags(flags: Int) {
        val values = contentValuesOf(RawContactColumns.FLAGS to flags)
        provider.update(androidContact.rawContactSyncURI(), values, null, null)
        androidContact.flags = flags
    }

    override fun updateSequence(sequence: Int) = throw NotImplementedError()

    override fun updateUid(uid: String) {
        val values = contentValuesOf(RawContactColumns.UID to uid)
        provider.update(androidContact.rawContactSyncURI(), values, null, null)
    }

    override fun deleteLocal() {
        androidContact.delete()
    }

    override fun resetDeleted() {
        val values = contentValuesOf(ContactsContract.Groups.DELETED to 0)
        provider.update(androidContact.rawContactSyncURI(), values, null, null)
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
