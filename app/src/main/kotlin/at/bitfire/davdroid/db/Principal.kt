/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.property.DisplayName
import at.bitfire.dav4jvm.property.ResourceType
import okhttp3.HttpUrl
import org.apache.commons.lang3.StringUtils

@Entity(tableName = "principal",
    foreignKeys = [
        ForeignKey(entity = Service::class, parentColumns = arrayOf("id"), childColumns = arrayOf("serviceId"), onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        // index by service, urls are unique
        Index("serviceId", "url", unique = true)
    ]
)
data class Principal(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var serviceId: Long,
    /** URL of the principal, always without trailing slash */
    var url: HttpUrl,
    var displayName: String? = null
) {

    companion object {

        /**
         * Generates a principal entity from a WebDAV response.
         * @param dav WebDAV response (make sure that you have queried `DAV:resource-type` and `DAV:display-name`)
         * @return generated principal data object (with `id`=0), `null` if the response doesn't represent a principal
         */
        fun fromDavResponse(serviceId: Long, dav: Response): Principal? {
            // Check if response is a principal
            val resourceType = dav[ResourceType::class.java] ?: return null
            if (!resourceType.types.contains(ResourceType.PRINCIPAL))
                return null

            // Try getting the display name of the principal
            val displayName: String? = StringUtils.trimToNull(
                dav[DisplayName::class.java]?.displayName
            )

            // Create and return principal - even without it's display name
            return Principal(
                serviceId = serviceId,
                url = UrlUtils.omitTrailingSlash(dav.href),
                displayName = displayName
            )
        }

        fun fromServiceAndUrl(service: Service, url: HttpUrl) = Principal(
            serviceId = service.id,
            url = UrlUtils.omitTrailingSlash(url)
        )

    }

}