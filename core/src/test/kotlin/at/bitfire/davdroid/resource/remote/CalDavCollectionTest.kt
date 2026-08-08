/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.davdroid.sync.unwrapContext
import at.bitfire.synctools.test.assertThrows
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CalDavCollectionTest {

    private val url = Url("https://example.com/dav/calendar/")

    @Test
    fun `listFilteredMembers() sends one calendar-query REPORT per filtered component`() = runTest {
        val engine = minimalMultiStatus()
        val calendar = collection(engine, CalendarQueryFilter(components = listOf("VEVENT", "VTODO")))

        calendar.listFilteredMembers().toList()

        assertEquals(2, engine.requestHistory.size)
        assertTrue(requestBody(engine, 0).contains("comp-filter name=\"VEVENT\""))
        assertTrue(requestBody(engine, 1).contains("comp-filter name=\"VTODO\""))
    }

    @Test
    fun `listFilteredMembers() applies the time range from the filter`() = runTest {
        val engine = minimalMultiStatus()
        val start = Instant.parse("2024-01-01T00:00:00Z")
        val end = Instant.parse("2024-02-01T00:00:00Z")
        val calendar = collection(
            engine,
            CalendarQueryFilter(components = listOf("VEVENT"), timeRangeStart = start, timeRangeEnd = end)
        )

        calendar.listFilteredMembers().toList()

        val body = requestBody(engine, 0)
        assertTrue(body.contains("time-range"))
        assertTrue(body.contains("20240101T000000Z"))
        assertTrue(body.contains("20240201T000000Z"))
    }

    @Test
    fun `listFilteredMembers() maps member responses to MemberStates`() = runTest {
        val calendar = collection(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>/dav/calendar/event1.ics</href>
                <propstat>
                  <prop>
                    <getetag>"event-etag"</getetag>
                  </prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
            """.trimIndent(),
            CalendarQueryFilter(components = listOf("VEVENT"))
        )

        val members = calendar.listFilteredMembers().toList()

        assertEquals(
            listOf(MemberState(Url("https://example.com/dav/calendar/event1.ics"), "event-etag")),
            members
        )
    }

    private fun minimalMultiStatus() = MockEngine { _ ->
        respond(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><multistatus xmlns=\"DAV:\"/>",
            HttpStatusCode.MultiStatus,
            headersOf(HttpHeaders.ContentType, "text/xml")
        )
    }

    private suspend fun requestBody(engine: MockEngine, index: Int) =
        engine.requestHistory[index].body.toByteArray().toString(Charsets.UTF_8)

    @Test
    fun `multiget() extracts calendar-data into MultiGetItem`() = runTest {
        val calendar = collection(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <response>
                <href>/dav/calendar/event1.ics</href>
                <propstat>
                  <prop>
                    <getetag>"event-etag"</getetag>
                    <C:schedule-tag>"event-schedule-tag"</C:schedule-tag>
                    <C:calendar-data>BEGIN:VCALENDAR
            END:VCALENDAR</C:calendar-data>
                  </prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
            """.trimIndent()
        )

        val items = calendar.multiget(
            listOf(Url("https://example.com/dav/calendar/event1.ics")),
            WebDavCollection.Capabilities()
        ).toList()

        assertEquals(
            listOf(
                WebDavCollection.MultiGetItem(
                    url = Url("https://example.com/dav/calendar/event1.ics"),
                    eTag = "event-etag",
                    scheduleTag = "event-schedule-tag",
                    content = "BEGIN:VCALENDAR\nEND:VCALENDAR"
                )
            ),
            items
        )
    }

    @Test
    fun `multiget() ignores the collection's own response`() = runTest {
        val calendar = collection(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>/dav/calendar/</href>
                <propstat>
                  <prop>
                    <getetag>"collection-etag"</getetag>
                  </prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
            """.trimIndent()
        )

        val items = calendar.multiget(listOf(Url("https://example.com/dav/calendar/")), WebDavCollection.Capabilities())
            .toList()

        assertTrue(items.isEmpty())
    }

    @Test
    fun `multiget() throws when a member response lacks calendar-data`() = runTest {
        val calendar = collection(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>/dav/calendar/event1.ics</href>
                <propstat>
                  <prop>
                    <getetag>"event-etag"</getetag>
                  </prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
            """.trimIndent()
        )

        val e = assertThrows<Throwable> {
            calendar.multiget(
                listOf(Url("https://example.com/dav/calendar/event1.ics")),
                WebDavCollection.Capabilities()
            ).toList()
        }

        assertEquals("Received multi-get response without data", e.unwrapContext().cause.message)
    }

    private fun collection(
        xmlResponse: String,
        filter: CalendarQueryFilter = CalendarQueryFilter(components = listOf("VEVENT"))
    ): CalDavCollection {
        val engine = MockEngine { _ ->
            respond(xmlResponse, HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, "text/xml"))
        }
        return collection(engine, filter)
    }

    private fun collection(engine: MockEngine, filter: CalendarQueryFilter): CalDavCollection =
        CalDavCollection(HttpClient(engine), url, filter)

}
