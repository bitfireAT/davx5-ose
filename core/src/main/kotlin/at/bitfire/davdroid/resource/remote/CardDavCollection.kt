/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavAddressBook
import at.bitfire.dav4jvm.ktor.responsesWithRelation
import at.bitfire.dav4jvm.property.carddav.AddressData
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.util.DavUtils
import ezvcard.VCardVersion
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Remote CardDAV collection, as used for address books.
 */
class CardDavCollection(httpClient: HttpClient, url: Url) : BaseWebDavCollection(httpClient, url) {

    override val davCollection = DavAddressBook(httpClient, url)

    /**
     * Lists all members using PROPFIND. CardDAV has no member-listing filter.
     *
     * We could use an RFC 6352 8.6 CARDDAV:addressbook-query Report, but an address book
     * "MUST only contain address object resources and collections that are not address book
     * collections" (section 5.2a) anyway, and PROPFIND is more compatible.
     */
    override fun listFilteredMembers(): Flow<MemberState> =
        davCollection.propfind(depth = 1, WebDAV.ResourceType, WebDAV.GetETag).toMemberStates()

    override fun multiget(
        urls: List<Url>,
        capabilities: WebDavCollection.Capabilities
    ): Flow<WebDavCollection.MultiGetItem> {
        val contentType = DavUtils.MEDIA_TYPE_VCARD.toString()
        val vCardVersion = if (capabilities.supportsVCard4)
            VCardVersion.V4_0.version
        else {
            // 3.0 is the default version; don't request it explicitly because some vCard3-only servers may not understand it
            null
        }

        return davCollection.multiget(urls, contentType, vCardVersion).responsesWithRelation()
            .filterMembers()
            .filterSuccessful()
            .map {
                it.response.asMultiGetItem { r -> r[AddressData::class.java]?.card }
            }
    }

}
