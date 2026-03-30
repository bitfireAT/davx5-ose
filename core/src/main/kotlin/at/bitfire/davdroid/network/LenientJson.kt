/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import kotlinx.serialization.json.Json

/** A lenient JSON serializer/parser configuration that is as compatible as possible. */
val lenientJson get() = Json {
    // we want to be as compatible as possible
    isLenient = true            // don't be unnecessarily strict
    ignoreUnknownKeys = true    // ignore unknown keys
}
