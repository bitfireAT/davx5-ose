/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavAddressBook
import io.ktor.client.HttpClient
import io.ktor.http.Url

/**
 * Remote CardDAV collection, as used for address books.
 */
class CardDavCollection(httpClient: HttpClient, url: Url) : BaseWebDavCollection(httpClient, url) {

    override val davCollection = DavAddressBook(httpClient, url)

}
