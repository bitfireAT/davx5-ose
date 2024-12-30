/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.annotation.StringDef
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import okhttp3.HttpUrl

/**
 * A service entity.
 *
 * Services represent accounts and are unique. They are of type CardDAV or CalDAV and may have an associated principal.
 */
@Entity(
    tableName = "service",
    foreignKeys = [
        ForeignKey(entity = Account::class, parentColumns = ["name"], childColumns = ["accountName"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE)
    ],
    indices = [
        // only one service per type and account
        Index("accountName", "type", unique = true)
    ]
)
data class Service(
    @PrimaryKey(autoGenerate = true)
    var id: Long,

    var accountName: String,
    @ServiceTypeDef var type: String,

    var principal: HttpUrl?
) {

    companion object {

        @StringDef(TYPE_CALDAV, TYPE_CARDDAV)
        annotation class ServiceTypeDef

        const val TYPE_CALDAV = "caldav"
        const val TYPE_CARDDAV = "carddav"
    }

}