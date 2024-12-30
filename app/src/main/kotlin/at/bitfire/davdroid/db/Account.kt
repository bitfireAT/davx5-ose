/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an account, which again has services (CalDAV/CardDAV).
 */
@Entity(
    tableName = "account",
    indices = [
        Index("name", unique = true)
    ]
)
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val name: String
)