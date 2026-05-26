/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
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
