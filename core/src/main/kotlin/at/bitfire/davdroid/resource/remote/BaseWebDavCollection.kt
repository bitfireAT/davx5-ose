/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.QuotedStringUtils
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.selfResponse
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.carddav.SupportedAddressData
import at.bitfire.dav4jvm.property.webdav.SupportedReportSet
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.resource.SyncState
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.headers
import io.ktor.util.appendAll
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize as CalDavMaxResourceSize
import at.bitfire.dav4jvm.property.carddav.MaxResourceSize as CardDavMaxResourceSize

/**
 * Common implementation of [WebDavCollection] for [CalDavCollection] and [CardDavCollection].
 */
abstract class BaseWebDavCollection(
    protected val httpClient: HttpClient,
    protected val url: Url
) : WebDavCollection {

    override suspend fun queryCapabilities(): WebDavCollection.QueryCapabilitiesResult {
        val response = davCollection.propfind(
            depth = 0,
            // WebDAV
            WebDAV.SupportedReportSet,
            WebDAV.SyncToken,
            // CalDAV
            CalDAV.GetCTag,
            CalDAV.MaxResourceSize,
            // CardDAV
            CardDAV.MaxResourceSize,
            CardDAV.SupportedAddressData
        ).selfResponse() ?: return WebDavCollection.QueryCapabilitiesResult(
            syncState = null,
            capabilities = WebDavCollection.Capabilities()
        )

        return WebDavCollection.QueryCapabilitiesResult(
            syncState = response.syncState(),
            capabilities = WebDavCollection.Capabilities(
                canCollectionSync = response[SupportedReportSet::class.java]?.reports?.contains(WebDAV.SyncCollection) == true,
                maxCalResourceSize = response[CalDavMaxResourceSize::class.java]?.maxSize,
                maxCardResourceSize = response[CardDavMaxResourceSize::class.java]?.maxSize,
                supportsVCard4 = response[SupportedAddressData::class.java]?.hasVCard4() == true
            )
        )
    }

    override suspend fun querySyncState(): SyncState? =
        davCollection.propfind(depth = 0, CalDAV.GetCTag, WebDAV.SyncToken).selfResponse()?.syncState()

    override suspend fun deleteMember(
        fileName: String,
        ifETag: String?,
        ifScheduleTag: String?,
        additionalHeaders: Map<String, String>
    ) {
        val memberUrl = URLBuilder(url).appendPathSegments(fileName, encodeSlash = true).build()
        DavResource(httpClient, memberUrl).delete(
            additionalHeaders = headers {
                if (ifETag != null)
                // only delete specific version
                    append(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
                if (ifScheduleTag != null)
                // only delete specific version
                    append(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))

                appendAll(additionalHeaders)
            }
        ) {}
    }

}
