package at.bitfire.davdroid.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import okhttp3.HttpUrl

/**
 * Represents a Webcal subscription, either standalone or linked to a CalDAV collection.
 */
@Entity(
    tableName = "webcal_subscription",
    foreignKeys = [
        ForeignKey(entity = Collection::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index("collectionId"),
        Index("url", unique = true)
    ]
)
data class WebcalSubscription(
    @PrimaryKey(autoGenerate = true)
    var id: Long,

    /** optional link to a CalDAV collection (typically used when the subscription has been
     *  created from the WebCAL tab of a CalDAV account) **/
    var collectionId: Long?,

    /** system calendar ID **/
    var calendarId: Long?,

    var url: HttpUrl,
    var displayName: String? = null,
    var color: Long,

    var eTag: String? = null,
    var lastModified: Long? = null,
    var lastSynchronized: Long? = null,
    var error: String? = null
)