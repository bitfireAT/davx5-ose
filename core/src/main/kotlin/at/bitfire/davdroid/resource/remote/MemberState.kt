/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.davdroid.util.DavUtils.lastSegment
import io.ktor.http.Url

/**
 * State of a member (non-collection resource) of a remote collection, as returned by
 * [WebDavCollection.listFilteredMembers].
 *
 * @param href  URL of the member
 * @param eTag  current ETag of the member
 */
data class MemberState(
    val href: Url,
    val eTag: String
) {

    /**
     * Decoded (unescaped) file name of the member, i.e. the last path segment of [href] with
     * percent-encoding resolved. Because it's decoded, it may contain characters like `/`
     * that would otherwise be path separators (for instance if the server percent-encoded
     * a `/` within the resource name as `%2F`).
     */
    val fileName: String
        get() = href.lastSegment

}
