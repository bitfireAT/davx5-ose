/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import java.time.Instant

data class DocumentState(
    val eTag: String? = null,
    val lastModified: Instant? = null
) {

    init {
        if (eTag == null && lastModified == null)
            throw IllegalArgumentException("Either ETag or Last-Modified is required")
    }

    override fun toString() =
        if (eTag != null)
            "eTag=$eTag"
        else
            "lastModified=$lastModified"

}