/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter.groups

import at.bitfire.vcard4android.Contact

interface ContactGroupStrategy {

    fun beforeUploadDirty()
    fun verifyContactBeforeSaving(contact: Contact)
    fun postProcess()

}