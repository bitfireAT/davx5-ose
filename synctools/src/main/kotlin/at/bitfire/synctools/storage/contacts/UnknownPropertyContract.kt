/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.contacts

import android.provider.ContactsContract.RawContacts

object UnknownPropertyContract {

    /** Column name for the MIME type of the data row. Type: [String] */
    const val MIMETYPE = RawContacts.Data.MIMETYPE

    /** MIME type of unknown-property data rows. Stored in [at.bitfire.synctools.storage.contacts.UnknownPropertyContract.MIMETYPE]. */
    const val CONTENT_ITEM_TYPE = "x.davdroid/unknown-properties"

    /** Column name for the ID of the raw contact this row belongs to. Type: [Long] */
    const val RAW_CONTACT_ID = RawContacts.Data.RAW_CONTACT_ID

    /** Column name for the serialized unknown vCard properties. Type: [String] */
    const val UNKNOWN_PROPERTIES = RawContacts.Data.DATA1

}
