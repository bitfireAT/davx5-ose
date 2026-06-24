/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import at.bitfire.dav4jvm.HttpUtils.toHttpUrl
import at.bitfire.dav4jvm.HttpUtils.toKtorUrl
import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.ktor.omitTrailingSlash
import at.bitfire.dav4jvm.okhttp.UrlUtils
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.synctools.util.trimToNull
import io.ktor.http.Url
import okhttp3.HttpUrl
import at.bitfire.dav4jvm.okhttp.Response as OkHttpResponse

/**
 * A principal entity representing a WebDAV principal (rfc3744).
 */
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
    val id: Long = 0,
    val serviceId: Long,
    /** URL of the principal, always without trailing slash */
    val url: Url,
    val displayName: String? = null
) {

    companion object {

        /**
         * Generates a principal entity from a WebDAV response.
         * @param dav WebDAV response (make sure that you have queried `DAV:resource-type` and `DAV:display-name`)
         * @return generated principal data object (with `id`=0), `null` if the response doesn't represent a principal
         */
        @Deprecated("Use Ktor overload")
        fun fromDavResponse(serviceId: Long, dav: OkHttpResponse): Principal? {
            // Check if response is a principal
            val resourceType = dav[ResourceType::class.java] ?: return null
            if (!resourceType.types.contains(WebDAV.Principal))
                return null

            // Try getting the display name of the principal
            val displayName: String? = dav[DisplayName::class.java]?.displayName.trimToNull()

            // Create and return principal - even without it's display name
            return Principal(
                serviceId = serviceId,
                url = UrlUtils.omitTrailingSlash(dav.href).toKtorUrl(),
                displayName = displayName
            )
        }

        /**
         * Generates a principal entity from a WebDAV response.
         * @param dav WebDAV response (make sure that you have queried `DAV:resource-type` and `DAV:display-name`)
         * @return generated principal data object (with `id`=0), `null` if the response doesn't represent a principal
         */
        fun fromDavResponse(serviceId: Long, dav: Response): Principal? {
            // Check if response is a principal
            val resourceType = dav[ResourceType::class.java] ?: return null
            if (!resourceType.types.contains(WebDAV.Principal))
                return null

            // Try getting the display name of the principal
            val displayName: String? = dav[DisplayName::class.java]?.displayName.trimToNull()

            // Create and return principal - even without its display name
            return Principal(
                serviceId = serviceId,
                url = dav.href.omitTrailingSlash(),
                displayName = displayName
            )
        }

        @Deprecated("Use Ktor overload")
        fun fromServiceAndUrl(service: Service, url: HttpUrl) = Principal(
            serviceId = service.id,
            url = UrlUtils.omitTrailingSlash(url).toKtorUrl()
        )

        fun fromServiceAndUrl(service: Service, url: Url) = Principal(
            serviceId = service.id,
            url = url.omitTrailingSlash()
        )

    }

}