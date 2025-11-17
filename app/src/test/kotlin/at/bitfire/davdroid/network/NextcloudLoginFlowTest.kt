/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import io.ktor.http.Url
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class NextcloudLoginFlowTest {

    private val flow = NextcloudLoginFlow(mockk(relaxed = true))

    @Test
    fun `loginFlowUrl accepts v2 URL`() {
        assertEquals(
            Url("http://example.com/index.php/login/v2"),
            flow.loginFlowUrl(Url("http://example.com/index.php/login/flow"))
        )
    }

    @Test
    fun `loginFlowUrl rewrites root URL to v2 URL`() {
        assertEquals(
            Url("http://example.com/index.php/login/v2"),
            flow.loginFlowUrl(Url("http://example.com/"))
        )
    }

    @Test
    fun `loginFlowUrl rewrites v1 URL to v2 URL`() {
        assertEquals(
            Url("http://example.com/index.php/login/v2"),
            flow.loginFlowUrl(Url("http://example.com/index.php/login/flow"))
        )
    }

}