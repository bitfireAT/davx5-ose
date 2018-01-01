/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model

import android.provider.ContactsContract.RawContacts

object UnknownProperties {

    @JvmField
    val CONTENT_ITEM_TYPE = "x.davdroid/unknown-properties"


    @JvmField
    val MIMETYPE = RawContacts.Data.MIMETYPE

    @JvmField
    val RAW_CONTACT_ID = RawContacts.Data.RAW_CONTACT_ID

    @JvmField
    val UNKNOWN_PROPERTIES = RawContacts.Data.DATA1

}
