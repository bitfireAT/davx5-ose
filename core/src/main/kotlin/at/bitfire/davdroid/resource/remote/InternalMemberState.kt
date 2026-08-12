/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.davdroid.util.DavUtils.lastSegment
import io.ktor.http.Url

/**
 * State of an internal member ("direct child") that is not a collection of a remote collection.
 *
 * @param href  URL of the member
 * @param eTag  current ETag of the member
 */
data class InternalMemberState(
    val href: Url,
    val eTag: String
) {

    /**
     * Decoded (unescaped) file name of the member, i.e. the last path segment of [href] with
     * percent-encoding resolved.
     *
     * **Attention:** Because it's decoded, it may contain characters like `/`  that would
     * otherwise be path separators.
     */
    val fileName: String?
        get() = href.lastSegment

}
