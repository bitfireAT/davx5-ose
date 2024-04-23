/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A [WebDavMount] with an optional root document (that contains information like quota).
 */
data class WebDavMountWithRootDocument(
    @Embedded
    val mount: WebDavMount,

    @Relation(
        parentColumn = "id",
        entityColumn = "mountId"
    )
    val rootDocument: WebDavDocument?
)