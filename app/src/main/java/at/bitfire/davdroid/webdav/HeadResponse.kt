/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import java.util.*

/**
 * Represents the information that was retrieved via a HEAD request before
 * accessing the file.
 */
data class HeadResponse(
    val size: Long? = null,
    val eTag: String? = null,
    val lastModified: Date? = null,

    val supportsPartial: Boolean? = null
) {
    fun toDocumentState(): DocumentState? =
        if (eTag != null || lastModified != null)
            DocumentState(eTag, lastModified)
        else
            null
}