package at.bitfire.davdroid.webdav

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