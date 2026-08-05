/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.ktor.DavCalendar
import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize
import at.bitfire.davdroid.sync.withExceptionContext
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.ZonedDateTime

/**
 * CalDAV collection access (calendars, task lists, jtx boards).
 *
 * @param components        iCalendar components to query, for instance `[VEVENT]` or `[VTODO, VJOURNAL]`
 * @param timeRangePastDays if set, only entries since that many days in the past are queried
 */
class CalDavCollection(
    private val davCalendar: DavCalendar,
    httpClient: HttpClient,
    private val components: List<String>,
    pushSubscription: String? = null,
    private val timeRangePastDays: Int? = null
) : AbstractWebDavCollection(davCalendar, httpClient, pushSubscription) {

    override val capabilityProperties: Array<Property.Name> = arrayOf(CalDAV.MaxResourceSize)

    override fun capabilitiesOf(
        response: Response,
        base: WebDavCollection.Capabilities
    ): WebDavCollection.Capabilities =
        base.copy(maxResourceSize = response[MaxResourceSize::class.java]?.maxSize)

    override fun listAll(): Flow<MultiStatusItem> = flow {
        val limitStart = timeRangePastDays?.let { pastDays ->
            ZonedDateTime.now().minusDays(pastDays.toLong()).toInstant()
        }

        url.withExceptionContext {
            for (component in components) {
                logger.info("Querying $component")
                emitAll(davCalendar.calendarQuery(component, limitStart, null))
            }
        }
    }

    override fun downloadMembers(hrefs: List<Url>, capabilities: WebDavCollection.Capabilities): Flow<MultiStatusItem> =
        davCalendar.multiget(hrefs)

}
