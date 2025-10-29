/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import at.bitfire.vcard4android.Contact

interface LocalAddress: LocalResource {

    fun update(data: Contact, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int)

    fun updateUid(uid: String)

}