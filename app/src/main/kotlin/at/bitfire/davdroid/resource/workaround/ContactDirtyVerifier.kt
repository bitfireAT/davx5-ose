/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.workaround

import android.content.ContentValues
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.vcard4android.BatchOperation

interface ContactDirtyVerifier {

    // address-book level functions

    fun prepareAddressBook(addressBook: LocalAddressBook, isUpload: Boolean): Boolean


    // contact level functions

    fun setHashCodeColumn(contact: LocalContact, values: ContentValues)

    fun updateHashCode(addressBook: LocalAddressBook, contact: LocalContact)
    fun updateHashCode(contact: LocalContact, batch: BatchOperation)

}