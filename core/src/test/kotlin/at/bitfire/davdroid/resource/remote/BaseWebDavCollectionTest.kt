/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCollection
import at.bitfire.davdroid.resource.SyncState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseWebDavCollectionTest {

    private val url = Url("https://example.com/dav/")

    private fun collection(xmlResponse: String): BaseWebDavCollection {
        val engine = MockEngine { _ ->
            respond(xmlResponse, HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, "text/xml"))
        }
        return collection(engine)
    }

    private fun collection(engine: MockEngine): BaseWebDavCollection {
        val httpClient = HttpClient(engine)
        return object : BaseWebDavCollection(httpClient, url) {
            override val davCollection = DavCollection(httpClient, url)
            override fun multiget(
                urls: List<Url>,
                capabilities: WebDavCollection.Capabilities
            ): Flow<WebDavCollection.MultiGetItem> =
                throw NotImplementedError()
        }
    }


    // create

    @Test
    fun `createMember() sends PUT to the member URL`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.Created)
        }

        collection(engine).createMember("some-file.ics", TextContent("BEGIN:VCALENDAR", ContentType.Text.Plain))

        assertEquals(HttpMethod.Put, request?.method)
        assertEquals(Url("https://example.com/dav/some-file.ics"), request?.url)
    }

    @Test
    fun `createMember() sets If-None-Match`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.Created)
        }

        collection(engine).createMember("some-file.ics", TextContent("BEGIN:VCALENDAR", ContentType.Text.Plain))

        assertEquals("*", request?.headers?.get(HttpHeaders.IfNoneMatch))
    }

    @Test
    fun `createMember() passes through additionalHeaders`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.Created)
        }

        collection(engine).createMember(
            "some-file.ics",
            TextContent("BEGIN:VCALENDAR", ContentType.Text.Plain),
            additionalHeaders = mapOf("Push-Dont-Notify" to "\"some-subscription\"")
        )

        assertEquals("\"some-subscription\"", request?.headers?.get("Push-Dont-Notify"))
    }

    @Test
    fun `createMember() returns ETag and Schedule-Tag from response`() = runTest {
        val engine = MockEngine {
            respond(
                "",
                HttpStatusCode.Created,
                headersOf(
                    HttpHeaders.ETag to listOf("\"new-etag\""),
                    HttpHeaders.ScheduleTag to listOf("\"new-schedule-tag\"")
                )
            )
        }

        val result =
            collection(engine).createMember("some-file.ics", TextContent("BEGIN:VCALENDAR", ContentType.Text.Plain))

        assertEquals("new-etag", result.eTag)
        assertEquals("new-schedule-tag", result.scheduleTag)
    }


    // read/query

    @Test
    fun `queryCapabilities() with no self-response returns defaults`() = runTest {
        val result = collection(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<multistatus xmlns=\"DAV:\"/>"
        ).queryCapabilities()

        assertNull(result.syncState)
        assertEquals(WebDavCollection.Capabilities(), result.capabilities)
    }

    @Test
    fun `queryCapabilities() with GetCTag returns sync state`() = runTest {
        val result = collection(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<multistatus xmlns=\"DAV:\" xmlns:CS=\"http://calendarserver.org/ns/\">\n" +
                    "  <response>\n" +
                    "    <href>/dav/</href>\n" +
                    "    <propstat>\n" +
                    "      <prop>\n" +
                    "        <CS:getctag>abc123</CS:getctag>\n" +
                    "      </prop>\n" +
                    "      <status>HTTP/1.1 200 OK</status>\n" +
                    "    </propstat>\n" +
                    "  </response>\n" +
                    "</multistatus>"
        ).queryCapabilities()

        assertEquals(SyncState(SyncState.Type.CTAG, "abc123"), result.syncState)
    }

    @Test
    fun `queryCapabilities() prefers SyncToken over GetCTag`() = runTest {
        val result = collection(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<multistatus xmlns=\"DAV:\" xmlns:CS=\"http://calendarserver.org/ns/\">\n" +
                    "  <response>\n" +
                    "    <href>/dav/</href>\n" +
                    "    <propstat>\n" +
                    "      <prop>\n" +
                    "        <sync-token>http://example.com/ns/sync/token123</sync-token>\n" +
                    "        <CS:getctag>abc123</CS:getctag>\n" +
                    "      </prop>\n" +
                    "      <status>HTTP/1.1 200 OK</status>\n" +
                    "    </propstat>\n" +
                    "  </response>\n" +
                    "</multistatus>"
        ).queryCapabilities()

        assertEquals(SyncState(SyncState.Type.SYNC_TOKEN, "http://example.com/ns/sync/token123"), result.syncState)
    }

    @Test
    fun `queryCapabilities() with SupportedReportSet containing sync-collection sets canCollectionSync`() =
        runTest {
            val result = collection(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<multistatus xmlns=\"DAV:\">\n" +
                        "  <response>\n" +
                        "    <href>/dav/</href>\n" +
                        "    <propstat>\n" +
                        "      <prop>\n" +
                        "        <supported-report-set>\n" +
                        "          <supported-report>\n" +
                        "            <report>\n" +
                        "              <sync-collection/>\n" +
                        "            </report>\n" +
                        "          </supported-report>\n" +
                        "        </supported-report-set>\n" +
                        "      </prop>\n" +
                        "      <status>HTTP/1.1 200 OK</status>\n" +
                        "    </propstat>\n" +
                        "  </response>\n" +
                        "</multistatus>"
            ).queryCapabilities()

            assertTrue(result.capabilities.canCollectionSync)
        }

    @Test
    fun `queryCapabilities() with SupportedReportSet without sync-collection leaves canCollectionSync false`() =
        runTest {
            val result = collection(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<multistatus xmlns=\"DAV:\">\n" +
                        "  <response>\n" +
                        "    <href>/dav/</href>\n" +
                        "    <propstat>\n" +
                        "      <prop>\n" +
                        "        <supported-report-set>\n" +
                        "          <supported-report>\n" +
                        "            <report>\n" +
                        "              <version-tree/>\n" +
                        "            </report>\n" +
                        "          </supported-report>\n" +
                        "        </supported-report-set>\n" +
                        "      </prop>\n" +
                        "      <status>HTTP/1.1 200 OK</status>\n" +
                        "    </propstat>\n" +
                        "  </response>\n" +
                        "</multistatus>"
            ).queryCapabilities()

            assertFalse(result.capabilities.canCollectionSync)
        }

    @Test
    fun `queryCapabilities() with SupportedAddressData vCard4 sets supportsVCard4`() = runTest {
        val result = collection(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<multistatus xmlns=\"DAV:\" xmlns:CARDDAV=\"urn:ietf:params:xml:ns:carddav\">\n" +
                    "  <response>\n" +
                    "    <href>/dav/</href>\n" +
                    "    <propstat>\n" +
                    "      <prop>\n" +
                    "        <CARDDAV:supported-address-data>\n" +
                    "          <CARDDAV:address-data-type content-type=\"text/vcard\" version=\"4.0\"/>\n" +
                    "        </CARDDAV:supported-address-data>\n" +
                    "      </prop>\n" +
                    "      <status>HTTP/1.1 200 OK</status>\n" +
                    "    </propstat>\n" +
                    "  </response>\n" +
                    "</multistatus>"
        ).queryCapabilities()

        assertTrue(result.capabilities.supportsVCard4)
    }

    @Test
    fun `queryCapabilities() parses CalDAV max-resource-size`() = runTest {
        val result = collection(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<multistatus xmlns=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">\n" +
                    "  <response>\n" +
                    "    <href>/dav/</href>\n" +
                    "    <propstat>\n" +
                    "      <prop>\n" +
                    "        <C:max-resource-size>102400</C:max-resource-size>\n" +
                    "      </prop>\n" +
                    "      <status>HTTP/1.1 200 OK</status>\n" +
                    "    </propstat>\n" +
                    "  </response>\n" +
                    "</multistatus>"
        ).queryCapabilities()

        assertEquals(102400L, result.capabilities.maxCalResourceSize)
    }

    @Test
    fun `queryCapabilities() parses CardDAV max-resource-size`() = runTest {
        val result = collection(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<multistatus xmlns=\"DAV:\" xmlns:CARDDAV=\"urn:ietf:params:xml:ns:carddav\">\n" +
                    "  <response>\n" +
                    "    <href>/dav/</href>\n" +
                    "    <propstat>\n" +
                    "      <prop>\n" +
                    "        <CARDDAV:max-resource-size>204800</CARDDAV:max-resource-size>\n" +
                    "      </prop>\n" +
                    "      <status>HTTP/1.1 200 OK</status>\n" +
                    "    </propstat>\n" +
                    "  </response>\n" +
                    "</multistatus>"
        ).queryCapabilities()

        assertEquals(204800L, result.capabilities.maxCardResourceSize)
    }

    @Test
    fun `querySyncState() with no self-response returns null`() = runTest {
        val syncState = collection(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<multistatus xmlns=\"DAV:\"/>"
        ).querySyncState()

        assertNull(syncState)
    }

    @Test
    fun `querySyncState() with GetCTag returns sync state`() = runTest {
        val syncState = collection(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<multistatus xmlns=\"DAV:\" xmlns:CS=\"http://calendarserver.org/ns/\">\n" +
                    "  <response>\n" +
                    "    <href>/dav/</href>\n" +
                    "    <propstat>\n" +
                    "      <prop>\n" +
                    "        <CS:getctag>abc123</CS:getctag>\n" +
                    "      </prop>\n" +
                    "      <status>HTTP/1.1 200 OK</status>\n" +
                    "    </propstat>\n" +
                    "  </response>\n" +
                    "</multistatus>"
        ).querySyncState()

        assertEquals(SyncState(SyncState.Type.CTAG, "abc123"), syncState)
    }

    @Test
    fun `querySyncState() prefers SyncToken over GetCTag`() = runTest {
        val syncState = collection(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<multistatus xmlns=\"DAV:\" xmlns:CS=\"http://calendarserver.org/ns/\">\n" +
                    "  <response>\n" +
                    "    <href>/dav/</href>\n" +
                    "    <propstat>\n" +
                    "      <prop>\n" +
                    "        <sync-token>http://example.com/ns/sync/token123</sync-token>\n" +
                    "        <CS:getctag>abc123</CS:getctag>\n" +
                    "      </prop>\n" +
                    "      <status>HTTP/1.1 200 OK</status>\n" +
                    "    </propstat>\n" +
                    "  </response>\n" +
                    "</multistatus>"
        ).querySyncState()

        assertEquals(SyncState(SyncState.Type.SYNC_TOKEN, "http://example.com/ns/sync/token123"), syncState)
    }


    // update

    @Test
    fun `updateMember() sends PUT to the member URL`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.NoContent)
        }

        collection(engine).updateMember("some-file.ics", TextContent("BEGIN:VCALENDAR", ContentType.Text.Plain))

        assertEquals(HttpMethod.Put, request?.method)
        assertEquals(Url("https://example.com/dav/some-file.ics"), request?.url)
    }

    @Test
    fun `updateMember() sets If-Match when ifETag is given`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.NoContent)
        }

        collection(engine).updateMember(
            "some-file.ics",
            TextContent("BEGIN:VCALENDAR", ContentType.Text.Plain),
            ifETag = "some-etag"
        )

        assertEquals("\"some-etag\"", request?.headers?.get(HttpHeaders.IfMatch))
    }

    @Test
    fun `updateMember() sets If-Schedule-Tag-Match when ifScheduleTag is given`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.NoContent)
        }

        collection(engine).updateMember(
            "some-file.ics",
            TextContent("BEGIN:VCALENDAR", ContentType.Text.Plain),
            ifScheduleTag = "some-schedule-tag"
        )

        assertEquals("\"some-schedule-tag\"", request?.headers?.get(HttpHeaders.IfScheduleTagMatch))
    }

    @Test
    fun `updateMember() sets no precondition headers when no tags are given`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.NoContent)
        }

        collection(engine).updateMember("some-file.ics", TextContent("BEGIN:VCALENDAR", ContentType.Text.Plain))

        assertNull(request?.headers?.get(HttpHeaders.IfMatch))
        assertNull(request?.headers?.get(HttpHeaders.IfScheduleTagMatch))
    }

    @Test
    fun `updateMember() passes through additionalHeaders`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.NoContent)
        }

        collection(engine).updateMember(
            "some-file.ics",
            TextContent("BEGIN:VCALENDAR", ContentType.Text.Plain),
            additionalHeaders = mapOf("Push-Dont-Notify" to "\"some-subscription\"")
        )

        assertEquals("\"some-subscription\"", request?.headers?.get("Push-Dont-Notify"))
    }

    @Test
    fun `updateMember() returns ETag and Schedule-Tag from response`() = runTest {
        val engine = MockEngine {
            respond(
                "",
                HttpStatusCode.NoContent,
                headersOf(
                    HttpHeaders.ETag to listOf("\"updated-etag\""),
                    HttpHeaders.ScheduleTag to listOf("\"updated-schedule-tag\"")
                )
            )
        }

        val result =
            collection(engine).updateMember("some-file.ics", TextContent("BEGIN:VCALENDAR", ContentType.Text.Plain))

        assertEquals("updated-etag", result.eTag)
        assertEquals("updated-schedule-tag", result.scheduleTag)
    }


    // delete

    @Test
    fun `deleteMember() sends DELETE to the member URL`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.NoContent)
        }

        collection(engine).deleteMember("some-file.ics")

        assertEquals(HttpMethod.Delete, request?.method)
        assertEquals(Url("https://example.com/dav/some-file.ics"), request?.url)
    }

    @Test
    fun `deleteMember() sets If-Match when ifETag is given`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.NoContent)
        }

        collection(engine).deleteMember("some-file.ics", ifETag = "some-etag")

        assertEquals("\"some-etag\"", request?.headers?.get(HttpHeaders.IfMatch))
    }

    @Test
    fun `deleteMember() sets If-Schedule-Tag-Match when ifScheduleTag is given`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.NoContent)
        }

        collection(engine).deleteMember("some-file.ics", ifScheduleTag = "some-schedule-tag")

        assertEquals("\"some-schedule-tag\"", request?.headers?.get(HttpHeaders.IfScheduleTagMatch))
    }

    @Test
    fun `deleteMember() passes through additionalHeaders`() = runTest {
        var request: HttpRequestData? = null
        val engine = MockEngine { req ->
            request = req
            respond("", HttpStatusCode.NoContent)
        }

        collection(engine).deleteMember(
            "some-file.ics",
            additionalHeaders = mapOf("Push-Dont-Notify" to "\"some-subscription\"")
        )

        assertEquals("\"some-subscription\"", request?.headers?.get("Push-Dont-Notify"))
    }

}
