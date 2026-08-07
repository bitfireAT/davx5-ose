/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalDavCollectionTest {

    private val url = Url("https://example.com/dav/calendar/")

    private fun collection(xmlResponse: String): CalDavCollection {
        val engine = MockEngine { _ ->
            respond(xmlResponse, HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, "text/xml"))
        }
        return CalDavCollection(HttpClient(engine), url)
    }

    @Test
    fun `multiget() extracts calendar-data into MultiGetItem`() = runTest {
        val items = collection(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<multistatus xmlns=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n" +
                    "  <response>\n" +
                    "    <href>/dav/calendar/event1.ics</href>\n" +
                    "    <propstat>\n" +
                    "      <prop>\n" +
                    "        <getetag>\"event-etag\"</getetag>\n" +
                    "        <C:schedule-tag>\"event-schedule-tag\"</C:schedule-tag>\n" +
                    "        <C:calendar-data>BEGIN:VCALENDAR\nEND:VCALENDAR</C:calendar-data>\n" +
                    "      </prop>\n" +
                    "      <status>HTTP/1.1 200 OK</status>\n" +
                    "    </propstat>\n" +
                    "  </response>\n" +
                    "</multistatus>"
        ).multiget(listOf(Url("https://example.com/dav/calendar/event1.ics")), WebDavCollection.Capabilities()).toList()

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
    fun `multiget() ignores response without calendar-data`() = runTest {
        val items = collection(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<multistatus xmlns=\"DAV:\">\n" +
                    "  <response>\n" +
                    "    <href>/dav/calendar/</href>\n" +
                    "    <propstat>\n" +
                    "      <prop>\n" +
                    "        <getetag>\"collection-etag\"</getetag>\n" +
                    "      </prop>\n" +
                    "      <status>HTTP/1.1 200 OK</status>\n" +
                    "    </propstat>\n" +
                    "  </response>\n" +
                    "</multistatus>"
        ).multiget(listOf(Url("https://example.com/dav/calendar/")), WebDavCollection.Capabilities()).toList()

        assertTrue(items.isEmpty())
    }

}
