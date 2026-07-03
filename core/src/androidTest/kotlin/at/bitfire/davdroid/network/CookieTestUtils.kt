/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.davdroid.network.CookieTestUtils.cookie
import io.ktor.http.Cookie
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.renderSetCookieHeader
import org.junit.Assert.assertEquals

object CookieTestUtils {
    /**
     * Appends a `Set-Cookie` header with the representation of [cookie].
     */
    fun HeadersBuilder.cookie(cookie: Cookie) {
        append(HttpHeaders.SetCookie, renderSetCookieHeader(cookie))
    }

    /**
     * Makes sure all the cookies in [cookies] are present in the [headers] with the expected values.
     */
    fun assertCookiesValues(headers: Headers, vararg cookies: Pair<String, String>) {
        val cookieHeader = headers[HttpHeaders.Cookie] ?: return
        val values = cookieHeader.split(';').map { it.split('=', limit = 2) }
        println("values=$values")
        for ((name, value) in cookies) {
            val cookieValue = values.find { it[0].trim() == name }?.get(1)?.trim()
            assertEquals(value, cookieValue)
        }
    }
}
