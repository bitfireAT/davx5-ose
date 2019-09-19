/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri

class AddressBookProvider: ContentProvider() {

    override fun onCreate() = false
    override fun insert(p0: Uri, p1: ContentValues?) = null
    override fun query(p0: Uri, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?) = null
    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?) = 0
    override fun delete(p0: Uri, p1: String?, p2: Array<out String>?) = 0
    override fun getType(p0: Uri) = null

}
