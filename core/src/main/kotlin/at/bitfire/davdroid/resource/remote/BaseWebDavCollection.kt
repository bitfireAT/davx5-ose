/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCollection
import at.bitfire.dav4jvm.ktor.selfResponse
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.carddav.SupportedAddressData
import at.bitfire.dav4jvm.property.webdav.SupportedReportSet
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.resource.syncState
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize as CalDavMaxResourceSize
import at.bitfire.dav4jvm.property.carddav.MaxResourceSize as CardDavMaxResourceSize

/**
 * Common implementation of [WebDavCollection] for [CalDavCollection] and [CardDavCollection].
 */
abstract class BaseWebDavCollection(
    open override val davCollection: DavCollection
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
                supportsCollectionSync = response[SupportedReportSet::class.java]?.reports?.contains(WebDAV.SyncCollection) == true,
                maxCalResourceSize = response[CalDavMaxResourceSize::class.java]?.maxSize,
                maxCardResourceSize = response[CardDavMaxResourceSize::class.java]?.maxSize,
                supportsVCard4 = response[SupportedAddressData::class.java]?.hasVCard4() == true
            )
        )
    }

}
