/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.ktor.PropStat
import at.bitfire.dav4jvm.ktor.Response
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiStatusItemExtensionsTest {

    private val url = Url("https://example.com/dav/")

    private fun item(status: HttpStatusCode?, relation: Response.HrefRelation) = MultiStatusItem.Response(
        Response(
            url,
            Url("https://example.com/dav/some-file.ics"),
            status,
            listOf(PropStat(emptyList(), HttpStatusCode.OK))
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

}
