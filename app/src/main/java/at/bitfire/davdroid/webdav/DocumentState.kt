/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.webdav.cache.CacheUtils
import java.util.*

data class DocumentState(
    val eTag: String? = null,
    val lastModified: Date? = null
) {

    init {
        if (eTag == null && lastModified == null)
            throw IllegalArgumentException("Either ETag or Last-Modified is required")
    }

    fun asString(): String =
        when {
            eTag != null ->
                CacheUtils.md5("eTag", eTag)
            lastModified != null ->
                CacheUtils.md5("lastModified", lastModified)
            else ->
                throw IllegalStateException()
        }

}