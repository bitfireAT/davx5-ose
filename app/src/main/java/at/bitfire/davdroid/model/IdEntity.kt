package at.bitfire.davdroid.model

/**
 * A model with a primary ID. Must be overriden with `@PrimaryKey(autoGenerate = true)`.
 * Required for [DaoTools] so that ID fields of all model classes have the same schema.
 */
interface IdEntity {
    var id: Long
}