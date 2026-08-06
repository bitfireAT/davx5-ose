/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCollection
import at.bitfire.davdroid.resource.SyncState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
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
        return object : BaseWebDavCollection(DavCollection(HttpClient(engine), url)) {}
    }

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
    fun `queryCapabilities() with SupportedReportSet containing sync-collection sets supportsCollectionSync`() =
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

            assertTrue(result.capabilities.supportsCollectionSync)
        }

    @Test
    fun `queryCapabilities() with SupportedReportSet without sync-collection leaves supportsCollectionSync false`() =
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

            assertFalse(result.capabilities.supportsCollectionSync)
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

        assertEquals(102400L, result.capabilities.maxResourceSize)
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

        assertEquals(204800L, result.capabilities.maxResourceSize)
    }

}
