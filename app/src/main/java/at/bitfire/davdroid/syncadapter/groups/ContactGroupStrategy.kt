package at.bitfire.davdroid.syncadapter.groups

import at.bitfire.davdroid.resource.LocalAddress
import at.bitfire.vcard4android.Contact

interface ContactGroupStrategy {

    fun prepare()
    fun beforeUploadDirty()
    fun beforeGenerateUpload(local: LocalAddress)
    fun verifyContactBeforeSaving(contact: Contact)
    fun afterSavingContact(local: LocalAddress)
    fun postProcess()

}