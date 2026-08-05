/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavAddressBook
import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.sync.withExceptionContext
import at.bitfire.davdroid.util.DavUtils
import ezvcard.VCardVersion
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * CardDAV collection access (address books).
 */
class CardDavCollection(
    private val davAddressBook: DavAddressBook,
    httpClient: HttpClient,
    pushSubscription: String? = null
) : BaseWebDavCollection(davAddressBook, httpClient, pushSubscription) {

    override fun listAll(): Flow<MultiStatusItem> = flow {
        url.withExceptionContext {
            emitAll(davAddressBook.propfind(1, WebDAV.ResourceType, WebDAV.GetETag))
        }
    }

    override fun downloadMembers(hrefs: List<Url>, capabilities: WebDavCollection.Capabilities): Flow<MultiStatusItem> {
        val contentType: String?
        val version: String?
        if (capabilities.supportsVCard4) {
            contentType = DavUtils.MEDIA_TYPE_VCARD.toString()
            version = VCardVersion.V4_0.version
        } else {
            contentType = DavUtils.MEDIA_TYPE_VCARD.toString()
            version =
                null     // 3.0 is the default version; don't request 3.0 explicitly because maybe some vCard3-only servers don't understand it
        }
        return davAddressBook.multiget(hrefs, contentType, version)
    }

}
