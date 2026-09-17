/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCalendar
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.responsesWithRelation
import at.bitfire.dav4jvm.property.caldav.CalendarData
import io.ktor.client.HttpClient
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.logging.Level
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
     * In addition to the PROPFIND of [BaseWebDavCollection.queryCapabilities], sends an OPTIONS
     * request to find out whether the server supports Time Zones by Reference (RFC 7809).
     *
     * That capability is only advertised in the `DAV:` response header (RFC 7809, 3.1.1), so it
     * can't be fetched by PROPFIND. This costs one extra request per collection and sync run.
     */
    override suspend fun queryCapabilities(): WebDavCollection.QueryCapabilitiesResult {
        val result = super.queryCapabilities()

        val davCapabilities = try {
            davCollection.options().davCapabilities
        } catch (e: DavException) {
            // Some servers don't answer OPTIONS on collections. Just assume that the server doesn't support RFC 7809.
            logger.log(Level.WARNING, "Couldn't query OPTIONS of $url", e)
            emptySet()
        }

        return result.copy(
            capabilities = result.capabilities.copy(
                supportsTimeZonesByReference = CALENDAR_NO_TIMEZONE in davCapabilities
            )
        )
    }

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
        return davCollection.multiget(
            urls = urls,
            /* If the server supports RFC 7809, ask it not to send VTIMEZONEs of standard (IANA)
            time zones. We ignore them anyway and use the system time zone with the same TZID
            instead (see SystemAwareTimeZoneRegistry), so receiving them is unnecessary. */
            additionalHeaders =
                if (capabilities.supportsTimeZonesByReference)
                    headersOf(CALDAV_TIMEZONES, "F")
                else
                    null
        ).responsesWithRelation()
            .filterMembers()
            .filterSuccessful()
            .map {
                it.response.asMultiGetItem { r -> r[CalendarData::class.java]?.iCalendar }
            }
    }


    companion object {

        /** `DAV:` header capability that a server supporting RFC 7809 advertises (RFC 7809, 3.1.1) */
        internal const val CALENDAR_NO_TIMEZONE = "calendar-no-timezone"

        /** request header to ask for iCalendars with ("T") or without ("F") VTIMEZONEs (RFC 7809, 7.1) */
        internal const val CALDAV_TIMEZONES = "CalDAV-Timezones"

    }

}
