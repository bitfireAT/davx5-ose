/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import at.bitfire.davdroid.util.DavUtils.extractCollectionName
import io.ktor.http.Url

@Entity(tableName = "homeset",
        foreignKeys = [
            ForeignKey(entity = Service::class, parentColumns = ["id"], childColumns = ["serviceId"], onDelete = ForeignKey.CASCADE)
        ],
        indices = [
            // index by service; no duplicate URLs per service
            Index("serviceId", "url", unique = true)
        ]
)
data class HomeSet(
    @PrimaryKey(autoGenerate = true)
    val id: Long,

    val serviceId: Long,

    /**
     * Whether this homeset belongs to the [Service.principal] given by [serviceId].
     */
    val personal: Boolean,

    /**
     * URL of the home set - with trailing slash
     */
    val url: Url,

    val privBind: Boolean = true,

    val displayName: String? = null
) {
    init {
        require(url.encodedPath.endsWith('/')) { "Not a collection URL (path does not end with a slash): $url" }
    }

    fun title() = displayName ?: extractCollectionName(url)

}