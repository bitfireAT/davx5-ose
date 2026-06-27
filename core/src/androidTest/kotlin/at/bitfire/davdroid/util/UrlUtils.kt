/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import at.bitfire.dav4jvm.ktor.toUrlOrNull
import io.ktor.http.Url

/**
 * An unsafe call to convert a [String] to a [Url].
 *
 * Highly preferred to use [toUrlOrNull] instead, and handle nullability.
 * @throws IllegalArgumentException If the source string is not a valid URL.
 */
fun String.toUrl(): Url = toUrlOrNull() ?: throw IllegalArgumentException("The source string ($this) is not a valid URL")
