/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import androidx.annotation.VisibleForTesting
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.LinkedList

/**
 * Primitive cookie store that stores cookies in a (volatile) hash map.
 * Will be sufficient for session cookies.
 */
class MemoryCookieStore : CookieJar {

    data class StorageKey(
        val domain: String,
        val path: String,
        val name: String
    )

    private val storage = mutableMapOf<StorageKey, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        /* [RFC 6265 5.3 Storage Model]

        11.  If the cookie store contains a cookie with the same name,
        domain, and path as the newly created cookie:

            1.  Let old-cookie be the existing cookie with the same name,
            domain, and path as the newly created cookie.  (Notice that
            this algorithm maintains the invariant that there is at most
            one such cookie.)

            2.  If the newly created cookie was received from a "non-HTTP"
            API and the old-cookie's http-only-flag is set, abort these
            steps and ignore the newly created cookie entirely.

            3.  Update the creation-time of the newly created cookie to
            match the creation-time of the old-cookie.

            4.  Remove the old-cookie from the cookie store.
         */
        synchronized(storage) {
            storage.putAll(cookies.map {
                StorageKey(
                    domain = it.domain,
                    path = it.path,
                    name = it.name
                ) to it
            })
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = LinkedList<Cookie>()

        synchronized(storage) {
            val iter = storage.iterator()
            while (iter.hasNext()) {
                val (_, cookie) = iter.next()

                // remove expired cookies
                if (cookie.expiresAt <= System.currentTimeMillis()) {
                    iter.remove()
                    continue
                }

                // add applicable cookies to result
                if (cookie.matches(url))
                    cookies += cookie
            }
        }

        return cookies
    }

}