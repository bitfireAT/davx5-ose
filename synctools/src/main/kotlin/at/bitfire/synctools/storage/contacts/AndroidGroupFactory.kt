/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.content.ContentValues

interface AndroidGroupFactory<T: AndroidGroup> {

    fun fromProvider(addressBook: AndroidAddressBook, values: ContentValues): T

}
