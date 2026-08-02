/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import at.bitfire.synctools.util.SensitiveString

@Entity(
    tableName = "account_setting",
    foreignKeys = [
        ForeignKey(
            entity = DbAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("accountId", "key", unique = true)
    ]
)
data class AccountSetting(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val key: String,
    val value: String? = null,

    /**
     * Like [value], but for sensitive values (like passwords) that shouldn't be available as [String]
     * to avoid accidental dumping in logs etc.
     */
    val sensitiveValue: SensitiveString? = null
)
