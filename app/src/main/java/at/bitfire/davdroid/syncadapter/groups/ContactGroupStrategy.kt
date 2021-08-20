package at.bitfire.davdroid.syncadapter.groups

import at.bitfire.davdroid.resource.LocalAddress
import at.bitfire.vcard4android.Contact

interface ContactGroupStrategy {

    fun beforeUploadDirty()
    fun verifyContactBeforeSaving(contact: Contact)
    fun postProcess()

}