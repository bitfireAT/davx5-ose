/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import com.google.common.base.Strings

fun CharSequence?.trimToNull() = Strings.emptyToNull(this?.trim()?.toString())

fun String.withTrailingSlash() =
    if (this.endsWith('/'))
        this
    else
        "$this/"