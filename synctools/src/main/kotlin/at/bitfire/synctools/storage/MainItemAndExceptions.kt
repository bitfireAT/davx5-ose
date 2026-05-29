/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage

import android.content.Entity

/**
 * Represents a set of a local main item (event, task, journal) and associated exceptions that are
 * stored together.
 *
 * It consists of
 * - a (potentially recurring) main item,
 * - optional exceptions to this main item (exception instances, only useful if main item is recurring).
 */
data class MainItemAndExceptions(
    val main: Entity,
    val exceptions: List<Entity>
)
