/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.util.Log
import at.bitfire.davdroid.network.MemoryCookieStore
import okhttp3.CookieJar

class CookieStoreManager {

    init {
        Log.i("CookieStoreManager", "CookieStoreManager.init()")
    }

    private val cookieStores = mutableMapOf<Long, CookieJar>()

    fun forMount(mountId: Long) =
        cookieStores.getOrPut(mountId) {
            MemoryCookieStore()
        }

}