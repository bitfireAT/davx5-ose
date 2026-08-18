/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.HttpUtils
import io.ktor.http.Url

/**
 * State of an internal member ("direct child") that is not a collection of a remote collection.
 *
 * @param href  URL of the member (must have a non-empty file name)
 * @param eTag  current ETag of the member
 */
data class InternalMemberState(
    val href: Url,
    val eTag: String
) {

    /**
     * Decoded (unescaped) file name of the member, i.e. the last path segment of [href] with
     * percent-encoding resolved. Same as [HttpUtils.fileName] / `Response.hrefName()`.
     *
     * **Attention:** Because it's decoded, it may contain characters like `/`  that would
     * otherwise be path separators.
     */
    val fileName: String = HttpUtils.fileName(href).also { name ->
        require(name.isNotEmpty()) { "Internal member must have a file name: $href" }
    }

}
