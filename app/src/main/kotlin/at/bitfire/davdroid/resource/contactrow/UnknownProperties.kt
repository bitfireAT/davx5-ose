/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource.contactrow

import android.provider.ContactsContract.RawContacts

object UnknownProperties {

    const val CONTENT_ITEM_TYPE = "x.davdroid/unknown-properties"

    const val MIMETYPE = RawContacts.Data.MIMETYPE
    const val RAW_CONTACT_ID = RawContacts.Data.RAW_CONTACT_ID
    const val UNKNOWN_PROPERTIES = RawContacts.Data.DATA1

}
