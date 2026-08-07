/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.ktor.PropStat
import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.davdroid.sync.unwrapContext
import at.bitfire.synctools.test.assertThrows
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResponseExtensionsTest {

    private val url = Url("https://example.com/dav/")

    private fun response(status: HttpStatusCode?, properties: List<Property>) = Response(
        url,
        Url("https://example.com/dav/some-file.ics"),
        status,
        listOf(PropStat(properties, HttpStatusCode.OK))
    )

    @Test
    fun `asMultiGetItem() with successful response and content returns MultiGetItem`() = runTest {
        val item = response(null, listOf(GetETag("some-etag"), ScheduleTag("some-schedule-tag")))
            .asMultiGetItem { "BEGIN:VCALENDAR" }

        assertEquals(
            WebDavCollection.MultiGetItem(
                url = Url("https://example.com/dav/some-file.ics"),
                eTag = "some-etag",
                scheduleTag = "some-schedule-tag",
                content = "BEGIN:VCALENDAR"
            ),
            item
        )
    }

    @Test
    fun `asMultiGetItem() with unsuccessful response throws IllegalArgumentException`() = runTest {
        val response = response(HttpStatusCode.NotFound, listOf(GetETag("some-etag")))

        assertThrows(IllegalArgumentException("Must only be called for successful responses")) {
            response.asMultiGetItem { "BEGIN:VCALENDAR" }
        }
    }

    @Test
    fun `asMultiGetItem() with missing content throws DavException`() = runTest {
        val response = response(null, listOf(GetETag("some-etag")))

        val e = assertThrows<Throwable> { response.asMultiGetItem { null } }

        assertEquals("Received multi-get response without data", e.unwrapContext().cause.message)
    }

    @Test
    fun `asMultiGetItem() with missing ETag throws DavException`() = runTest {
        val response = response(null, emptyList())

        val e = assertThrows<Throwable> { response.asMultiGetItem { "BEGIN:VCALENDAR" } }

        assertEquals("Received multi-get response without ETag", e.unwrapContext().cause.message)
    }

}
