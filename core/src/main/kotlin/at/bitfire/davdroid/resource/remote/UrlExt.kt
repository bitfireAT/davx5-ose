/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments

/**
 * Returns the URL of a member (non-collection resource) with the given file name inside this collection URL.
 *
 * [fileName] is treated as a single, unencoded (decoded) file name — it's percent-encoded when appended,
 * including a literal `/` (which would otherwise be interpreted as an additional path segment).
 *
 * @param fileName unencoded file name of the member
 *
 * @return URL of the member
 */
internal fun Url.member(fileName: String): Url =
    URLBuilder(this).appendPathSegments(fileName, encodeSlash = true).build()
