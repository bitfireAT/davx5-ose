/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.jtx

import android.content.Entity

/**
 * Stores data of a jtx object in a form suitable for the jtx board's content provider.
 *
 * @param entity The main row values and sub-row values.
 * @param dataSubValues Sub-row values that might contain additional binary data associated with a specific sub-row.
 */
data class JtxEntity(
    val entity: Entity,
    val dataSubValues: List<DataSubValue> = emptyList(),
)
