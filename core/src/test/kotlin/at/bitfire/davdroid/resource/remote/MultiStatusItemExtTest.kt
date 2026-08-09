/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.ktor.PropStat
import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.synctools.test.assertThrows
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiStatusItemExtTest {

    private val url = Url("https://example.com/dav/")

    private fun item(
        status: HttpStatusCode?,
        relation: Response.HrefRelation,
        properties: List<Property> = emptyList()
    ) = MultiStatusItem.Response(
        Response(
            url,
            Url("https://example.com/dav/some-file.ics"),
            status,
            listOf(PropStat(properties, HttpStatusCode.OK))
        ),
        relation
    )

    @Test
    fun `filterMembers() keeps a MEMBER response`() = runTest {
        val kept = item(null, Response.HrefRelation.MEMBER)
        val result = flowOf(kept).filterMembers().toList()

        assertEquals(listOf(kept), result)
    }

    @Test
    fun `filterMembers() filters out a SELF response`() = runTest {
        val result = flowOf(item(null, Response.HrefRelation.SELF)).filterMembers().toList()

        assertEquals(emptyList<MultiStatusItem.Response>(), result)
    }

    @Test
    fun `filterNotCollections() keeps a non-collection response`() = runTest {
        val kept = item(null, Response.HrefRelation.MEMBER)
        val result = flowOf(kept).filterNotCollections().toList()

        assertEquals(listOf(kept), result)
    }

    @Test
    fun `filterNotCollections() filters out a collection response`() = runTest {
        val collection = item(
            null,
            Response.HrefRelation.MEMBER,
            properties = listOf(ResourceType(setOf(WebDAV.Collection)))
        )
        val result = flowOf(collection).filterNotCollections().toList()

        assertEquals(emptyList<MultiStatusItem.Response>(), result)
    }

    @Test
    fun `filterSuccessful() keeps a successful response`() = runTest {
        val kept = item(null, Response.HrefRelation.MEMBER)
        val result = flowOf(kept).filterSuccessful().toList()

        assertEquals(listOf(kept), result)
    }

    @Test
    fun `filterSuccessful() filters out an unsuccessful response`() = runTest {
        val result =
            flowOf(item(HttpStatusCode.NotFound, Response.HrefRelation.MEMBER)).filterSuccessful().toList()

        assertEquals(emptyList<MultiStatusItem.Response>(), result)
    }

    @Test
    fun `toInternalMemberStates() maps a member response to an InternalMemberState`() = runTest {
        val member = item(null, Response.HrefRelation.MEMBER, properties = listOf(GetETag("\"member-etag\"")))
        val result = flowOf(member).toInternalMemberStates().toList()

        assertEquals(
            listOf(InternalMemberState(Url("https://example.com/dav/some-file.ics"), "member-etag")),
            result
        )
    }

    @Test
    fun `toInternalMemberStates() filters out a SELF response`() = runTest {
        val self = item(null, Response.HrefRelation.SELF, properties = listOf(GetETag("\"collection-etag\"")))
        val result = flowOf(self).toInternalMemberStates().toList()

        assertEquals(emptyList<InternalMemberState>(), result)
    }

    @Test
    fun `toInternalMemberStates() filters out a collection response`() = runTest {
        val subCollection = item(
            null,
            Response.HrefRelation.MEMBER,
            properties = listOf(ResourceType(setOf(WebDAV.Collection)), GetETag("\"sub-collection-etag\""))
        )
        val result = flowOf(subCollection).toInternalMemberStates().toList()

        assertEquals(emptyList<InternalMemberState>(), result)
    }

    @Test
    fun `toInternalMemberStates() filters out a non-successful response`() = runTest {
        val removed = item(HttpStatusCode.NotFound, Response.HrefRelation.MEMBER)
        val result = flowOf(removed).toInternalMemberStates().toList()

        assertEquals(emptyList<InternalMemberState>(), result)
    }

    @Test
    fun `toInternalMemberStates() throws when a member response lacks an ETag`() = runTest {
        val member = item(null, Response.HrefRelation.MEMBER)

        val e = assertThrows<DavException> {
            flowOf(member).toInternalMemberStates().toList()
        }

        assertEquals("Server didn't provide ETag for https://example.com/dav/some-file.ics", e.message)
    }

}
