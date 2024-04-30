/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import okhttp3.HttpUrl

fun HttpUrl.lastSegment(): String =
    pathSegments.lastOrNull { it.isNotEmpty() } ?: "/"