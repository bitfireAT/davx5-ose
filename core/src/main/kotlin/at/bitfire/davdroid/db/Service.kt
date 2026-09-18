/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.annotation.StringDef
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.ktor.http.Url

@Retention(AnnotationRetention.SOURCE)
@StringDef(Service.TYPE_CALDAV, Service.TYPE_CARDDAV)
annotation class ServiceType

/**
 * A service entity.
 *
 * Services represent accounts and are unique. They are of type CardDAV or CalDAV and may have an associated principal.
 */
@Entity(
    tableName = "service",
    indices = [
        // only one service per type and account
        Index("accountName", "type", unique = true),
        // only one service per type and account ID
        Index("accountId", "type", unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = DbAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Service(
    @PrimaryKey(autoGenerate = true)
    val id: Long,

    // TODO: Reference accounts by their database ID once we've gotten rid of `LegacyAccount`.
    @Deprecated("Use accountId instead")
    val accountName: String,
    val accountId: Long? = null,

    @ServiceType
    val type: String,

    val principal: Url? = null
) {

    companion object {
        const val TYPE_CALDAV = "caldav"
        const val TYPE_CARDDAV = "carddav"
    }

}