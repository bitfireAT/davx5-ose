/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import io.ktor.http.Url
import okhttp3.HttpUrl

/**
 * Converts an okhttp [HttpUrl] to a Ktor [Url].
 */
fun HttpUrl.toKtorUrl() = Url(toString())
