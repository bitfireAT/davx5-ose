/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.content.ContentValues

interface AndroidContactFactory<T: AndroidContact> {

    fun fromProvider(addressBook: AndroidAddressBook<T, out AndroidGroup>, values: ContentValues): T

}
