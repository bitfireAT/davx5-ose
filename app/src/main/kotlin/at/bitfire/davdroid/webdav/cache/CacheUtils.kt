/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav.cache

import java.net.URLEncoder
import java.security.MessageDigest

object CacheUtils {

    fun md5(vararg data: Any): String {
        val str = data.joinToString("/") { entry ->
            URLEncoder.encode(entry.toString(), "UTF-8")
        }

        val md5 = MessageDigest.getInstance("MD5").digest(str.toByteArray())
        return md5.joinToString("") { b -> String.format("%02x", b) }
    }

}