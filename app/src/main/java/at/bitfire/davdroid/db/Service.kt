/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import okhttp3.HttpUrl

@Entity(tableName = "service",
        indices = [
            // only one service per type and account
            Index("accountName", "type", unique = true)
        ])
data class Service(
    @PrimaryKey(autoGenerate = true)
    override var id: Long,

    var accountName: String,
    var type: String,

    var principal: HttpUrl?
): IdEntity {

    companion object {
        const val TYPE_CALDAV = "caldav"
        const val TYPE_CARDDAV = "carddav"
    }

}