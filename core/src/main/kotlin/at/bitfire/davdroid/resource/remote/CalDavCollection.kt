/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCalendar
import at.bitfire.dav4jvm.ktor.responsesWithRelation
import at.bitfire.dav4jvm.property.caldav.CalendarData
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Remote CalDAV collection, as used for calendars, jtx board collections and task lists.
 */
class CalDavCollection(httpClient: HttpClient, url: Url) : BaseWebDavCollection(httpClient, url) {

    override val davCollection = DavCalendar(httpClient, url)

    override fun multiget(
        urls: List<Url>,
        capabilities: WebDavCollection.Capabilities
    ): Flow<WebDavCollection.MultiGetItem> =
        davCollection.multiget(urls).responsesWithRelation()
            .filterSuccessfulMembers()
            .map {
                it.response.asMultiGetItem { r -> r[CalendarData::class.java]?.iCalendar }
            }

}
