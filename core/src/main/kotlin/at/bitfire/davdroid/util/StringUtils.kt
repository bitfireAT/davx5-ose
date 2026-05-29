/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

fun String.withTrailingSlash() =
    if (this.endsWith('/'))
        this
    else
        "$this/"