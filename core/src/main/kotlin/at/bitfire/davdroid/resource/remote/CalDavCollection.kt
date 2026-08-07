/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCalendar
import io.ktor.client.HttpClient
import io.ktor.http.Url

/**
 * Remote CalDAV collection, as used for calendars, jtx board collections and task lists.
 */
class CalDavCollection(httpClient: HttpClient, url: Url) : BaseWebDavCollection(httpClient, url) {

    override val davCollection = DavCalendar(httpClient, url)

}
