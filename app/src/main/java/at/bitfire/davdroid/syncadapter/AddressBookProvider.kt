/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri

@Suppress("ImplicitNullableNothingType")
class AddressBookProvider: ContentProvider() {

    override fun onCreate() = false
    override fun insert(p0: Uri, p1: ContentValues?) = null
    override fun query(p0: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?) = null
    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?) = 0
    override fun delete(p0: Uri, p1: String?, p2: Array<out String>?) = 0
    override fun getType(p0: Uri) = null

}
