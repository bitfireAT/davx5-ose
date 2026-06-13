/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.accounts.Account
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts
import at.bitfire.synctools.storage.contacts.ContactContract.UnknownProperty.MIMETYPE

/**
 * How synctools uses some Android contacts sync columns and data rows.
 */
object ContactContract {

    fun Uri.asSyncAdapter(account: Account): Uri = buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
        .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
        .build()

    object UnknownProperty {
        /** Column name for the MIME type of the data row. Type: [String] */
        const val MIMETYPE = RawContacts.Data.MIMETYPE

        /** MIME type of unknown-property data rows. Stored in [MIMETYPE]. */
        const val CONTENT_ITEM_TYPE = "x.davdroid/unknown-properties"

        /** Column name for the ID of the raw contact this row belongs to. Type: [Long] */
        const val RAW_CONTACT_ID = RawContacts.Data.RAW_CONTACT_ID

        /** Column name for the serialized unknown vCard properties. Type: [String] */
        const val UNKNOWN_PROPERTIES = RawContacts.Data.DATA1
    }

}