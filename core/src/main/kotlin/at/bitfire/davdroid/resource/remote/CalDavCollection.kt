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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.logging.Logger

/**
 * Remote CalDAV collection, as used for calendars, jtx board collections and task lists.
 *
 * @param filter restricts [listFilteredMembers] to certain components / a time range
 */
class CalDavCollection(
    httpClient: HttpClient,
    url: Url,
    private val filter: CalendarQueryFilter
) : BaseWebDavCollection(httpClient, url) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override val davCollection = DavCalendar(httpClient, url)

    /**
     * Lists the members matching [filter], using one `calendar-query` REPORT per component.
     *
     * Doesn't request [at.bitfire.dav4jvm.property.webdav.WebDAV.ResourceType]: per RFC 4791 §7.8,
     * a `calendar-query` REPORT only ever returns calendar object resources matching the
     * `comp-filter`, never collections, so there's nothing to filter out here.
     */
    override fun listFilteredMembers(): Flow<InternalMemberState> = flow {
        for (component in filter.components) {
            logger.info("Querying $component components since ${filter.timeRangeStart}")
            emitAll(
                davCollection
                    .calendarQuery(
                        component = component,
                        start = filter.timeRangeStart,
                        end = filter.timeRangeEnd
                    )
                    .toInternalMemberStates()
            )
        }
    }

    override fun multiget(
        urls: List<Url>,
        capabilities: WebDavCollection.Capabilities
    ): Flow<WebDavCollection.MultiGetItem> {
        logger.info("Downloading ${urls.size} calendar object resources: $urls")
        return davCollection.multiget(urls).responsesWithRelation()
            .filterMembers()
            .filterSuccessful()
            .map {
                it.response.asMultiGetItem { r -> r[CalendarData::class.java]?.iCalendar }
            }
    }

}
