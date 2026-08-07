/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.davdroid.sync.unwrapContext
import at.bitfire.synctools.test.assertThrows
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

    @Test
    fun `multiget() extracts calendar-data into MultiGetItem`() = runTest {
        val items = collection(
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
        val flow = collection(
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
        ).multiget(listOf(Url("https://example.com/dav/calendar/event1.ics")), WebDavCollection.Capabilities())

        val e = assertThrows<Throwable> { flow.toList() }
        assertEquals("Received multi-get response without data", e.unwrapContext().cause.message)
    }

    private fun collection(xmlResponse: String): CalDavCollection {
        val engine = MockEngine { _ ->
            respond(xmlResponse, HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, "text/xml"))
        }
        return CalDavCollection(HttpClient(engine), url)
    }

}
