/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import at.bitfire.vcard4android.Contact

interface LocalAddress: LocalResource<Contact> {

    fun resetDeleted()

}