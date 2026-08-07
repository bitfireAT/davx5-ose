/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavAddressBook
import at.bitfire.dav4jvm.ktor.responses
import at.bitfire.dav4jvm.property.carddav.AddressData
import at.bitfire.davdroid.util.DavUtils
import ezvcard.VCardVersion
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Remote CardDAV collection, as used for address books.
 */
class CardDavCollection(httpClient: HttpClient, url: Url) : BaseWebDavCollection(httpClient, url) {

    override val davCollection = DavAddressBook(httpClient, url)

    override fun multiget(
        urls: List<Url>,
        capabilities: WebDavCollection.Capabilities
    ): Flow<WebDavCollection.MultiGetItem> {
        val contentType = DavUtils.MEDIA_TYPE_VCARD.toString()
        // 3.0 is the default version; don't request it explicitly because maybe some vCard3-only servers don't understand it
        val version = if (capabilities.supportsVCard4) VCardVersion.V4_0.version else null
        return davCollection.multiget(urls, contentType, version).responses().mapNotNull { response ->
            buildMultiGetItem(response, response[AddressData::class.java]?.card)
        }
    }

}
