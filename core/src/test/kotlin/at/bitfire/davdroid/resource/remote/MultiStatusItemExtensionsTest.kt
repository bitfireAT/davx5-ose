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
    fun `filterSuccessfulMembers() keeps a successful MEMBER response`() = runTest {
        val kept = item(null, Response.HrefRelation.MEMBER)
        val result = flowOf(kept).filterSuccessfulMembers().toList()

        assertEquals(listOf(kept), result)
    }

    @Test
    fun `filterSuccessfulMembers() filters out a SELF response`() = runTest {
        val result = flowOf(item(null, Response.HrefRelation.SELF)).filterSuccessfulMembers().toList()

        assertEquals(emptyList<MultiStatusItem.Response>(), result)
    }

    @Test
    fun `filterSuccessfulMembers() filters out an unsuccessful MEMBER response`() = runTest {
        val result =
            flowOf(item(HttpStatusCode.NotFound, Response.HrefRelation.MEMBER)).filterSuccessfulMembers().toList()

        assertEquals(emptyList<MultiStatusItem.Response>(), result)
    }

}
