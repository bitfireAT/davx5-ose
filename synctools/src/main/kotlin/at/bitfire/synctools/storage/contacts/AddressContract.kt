/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.accounts.Account
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts
import at.bitfire.synctools.storage.contacts.AddressContract.CachedGroupMembership.MIMETYPE
import at.bitfire.synctools.storage.contacts.AddressContract.UnknownProperty.MIMETYPE

/**
 * How synctools uses some Android contacts sync columns and data rows.
 */
object AddressContract {

    /**
     * Appends [ContactsContract.CALLER_IS_SYNCADAPTER] to prevent dirty-marking; optionally appends
     * [account] for collection-level URIs that need account filtering or assignment (independent of
     * the sync-adapter flag in the provider).
     */
    fun Uri.asSyncAdapter(account: Account? = null): Uri = buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .apply {
            if (account != null) {
                appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
                appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
            }
        }
        .build()

    /**
     * Represents a "cached group membership" row. Cached group memberships exist only
     * for one reason, which _only_ applies to the vCard4 (KIND/MEMBER) group method:
     *
     * Every group has its list of members. When a contact's group memberships are changed,
     * the contact is automatically set to dirty, but the group itself is not!
     *
     * So we keep a copy of all group membership rows as "cached memberships". At the
     * beginning of every sync, the group memberships of every contact are compared with
     * its cached group memberships. If they differ, the respective contact group
     * is set to dirty (because its memberships have changed).
     *
     * Cached group memberships must not be used for anything else that detecting dirty groups.
     */
    object CachedGroupMembership {
        /** Column name for the MIME type of the data row. Type: [String] */
        const val MIMETYPE = RawContacts.Data.MIMETYPE

        /** MIME type of cached group membership data rows. Stored in [MIMETYPE]. */
        const val CONTENT_ITEM_TYPE = "x.davdroid/cached-group-membership"

        /** Column name for the ID of the raw contact this cached membership belongs to. Type: [Long] */
        const val RAW_CONTACT_ID = RawContacts.Data.RAW_CONTACT_ID

        /** Column name for the ID of the group this cached membership refers to. Type: [Long] */
        const val GROUP_ID = RawContacts.Data.DATA1
    }

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