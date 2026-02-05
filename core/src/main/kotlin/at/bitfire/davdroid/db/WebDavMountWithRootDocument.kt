/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Embedded

/**
 * A [WebDavMount] with an optional root document (that contains information like quota).
 */
data class WebDavMountWithQuota(
    @Embedded
    val mount: WebDavMount,

    val quotaAvailable: Long? = null,
    val quotaUsed: Long? = null
)