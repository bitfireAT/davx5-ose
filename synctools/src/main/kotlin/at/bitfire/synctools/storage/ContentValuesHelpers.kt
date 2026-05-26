/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils

operator fun ContentValues.plusAssign(other: ContentValues) {
    putAll(other)
}

fun ContentValues.containsNotNull(field: String): Boolean =
    getAsString(field) != null

/**
 * Removes blank (empty or only white-space) [String] values from [ContentValues].
 *
 * @return the modified object (which is the same object as passed in; for chaining)
 */
fun ContentValues.removeBlank(): ContentValues {
    val iter = keySet().iterator()
    while (iter.hasNext()) {
        val obj = this[iter.next()]
        if (obj is CharSequence && obj.isBlank())
            iter.remove()
    }
    return this
}

/**
 * Returns the contents of the current row as a [android.content.ContentValues] object.
 *
 * Removes blank fields using [ContentValues.removeBlank].
 *
 * @return contents of the current row (blank fields removed)
 */
fun Cursor.toContentValues(): ContentValues {
    val values = ContentValues(columnCount)
    DatabaseUtils.cursorRowToContentValues(this, values)

    return values.removeBlank()
}