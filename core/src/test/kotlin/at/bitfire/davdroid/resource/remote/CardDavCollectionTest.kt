/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

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

class CardDavCollectionTest {

    private val url = Url("https://example.com/dav/contacts/")

    private fun minimalMultiStatus() = MockEngine { _ ->
        respond(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><multistatus xmlns=\"DAV:\"/>",
            HttpStatusCode.MultiStatus,
            headersOf(HttpHeaders.ContentType, "text/xml")
        )
    }

    private suspend fun requestBody(engine: MockEngine) =
        engine.requestHistory.last().body.toByteArray().toString(Charsets.UTF_8)

    @Test
    fun `multiget() extracts address-data into MultiGetItem`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<multistatus xmlns=\"DAV:\" xmlns:CARD=\"urn:ietf:params:xml:ns:carddav\">\n" +
                        "  <response>\n" +
                        "    <href>/dav/contacts/contact1.vcf</href>\n" +
                        "    <propstat>\n" +
                        "      <prop>\n" +
                        "        <getetag>\"contact-etag\"</getetag>\n" +
                        "        <CARD:address-data>BEGIN:VCARD\nEND:VCARD</CARD:address-data>\n" +
                        "      </prop>\n" +
                        "      <status>HTTP/1.1 200 OK</status>\n" +
                        "    </propstat>\n" +
                        "  </response>\n" +
                        "</multistatus>",
                HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, "text/xml")
            )
        }

        val items = CardDavCollection(HttpClient(engine), url).multiget(
            listOf(Url("https://example.com/dav/contacts/contact1.vcf")),
            WebDavCollection.Capabilities()
        ).toList()

        assertEquals(
            listOf(
                WebDavCollection.MultiGetItem(
                    url = Url("https://example.com/dav/contacts/contact1.vcf"),
                    eTag = "contact-etag",
                    content = "BEGIN:VCARD\nEND:VCARD"
                )
            ),
            items
        )
    }

    @Test
    fun `multiget() with supportsVCard4 requests vCard 4`() = runTest {
        val engine = minimalMultiStatus()
        CardDavCollection(HttpClient(engine), url).multiget(
            listOf(Url("https://example.com/dav/contacts/contact1.vcf")),
            WebDavCollection.Capabilities(supportsVCard4 = true)
        ).toList()

        val body = requestBody(engine)
        assertTrue(body.contains("version=\"4.0\""))
    }

    @Test
    fun `multiget() without supportsVCard4 doesn't request a specific version`() = runTest {
        val engine = minimalMultiStatus()
        CardDavCollection(HttpClient(engine), url).multiget(
            listOf(Url("https://example.com/dav/contacts/contact1.vcf")),
            WebDavCollection.Capabilities(supportsVCard4 = false)
        ).toList()

        val body = requestBody(engine)
        assertTrue(body.contains("<CARD:address-data content-type=\"text/vcard\" />"))
    }

}
