/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.workaround

import android.content.ContentValues
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.vcard4android.BatchOperation

/**
 * Only required for [Android7DirtyVerifier]. If that class is removed because the minimum SDK is raised to Android 8,
 * this interface and all calls to it can be removed as well.
 */
interface ContactDirtyVerifier {

    // address-book level functions

    /**
     * Checks whether contacts which are marked as "dirty" are really dirty, i.e. their data has changed.
     * If contacts are not really dirty (because only the metadata like "last contacted" changed), the "dirty" flag is removed.
     *
     * Intended to be called by [at.bitfire.davdroid.sync.ContactsSyncManager.prepare].
     *
     * @param addressBook   the address book
     * @param isUpload      whether this sync is an upload
     *
     * @return `true` if the address book should be synced, `false` if the sync is an upload and no contacts have been changed
     */
    fun prepareAddressBook(addressBook: LocalAddressBook, isUpload: Boolean): Boolean


    // contact level functions

    /**
     * Sets the [LocalContact.COLUMN_HASHCODE] column in the given [ContentValues] to the hash code of the contact data.
     *
     * @param contact   the contact to calculate the hash code for
     * @param toValues  set the hash code into these values
     */
    fun setHashCodeColumn(contact: LocalContact, toValues: ContentValues)

    /**
     * Sets the [LocalContact.COLUMN_HASHCODE] field of the contact to the hash code of the contact data directly in the content provider.
     */
    fun updateHashCode(addressBook: LocalAddressBook, contact: LocalContact)

    /**
      Sets the [LocalContact.COLUMN_HASHCODE] field of the contact to the hash code of the contact data in a content provider batch operation.
     */
    fun updateHashCode(contact: LocalContact, batch: BatchOperation)

}