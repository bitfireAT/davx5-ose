/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

/**
 * A model with a primary ID. Must be overriden with `@PrimaryKey(autoGenerate = true)`.
 * Required for [DaoTools] so that ID fields of all model classes have the same schema.
 */
interface IdEntity {
    var id: Long
}