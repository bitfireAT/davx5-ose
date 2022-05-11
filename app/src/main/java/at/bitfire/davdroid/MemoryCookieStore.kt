/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.apache.commons.collections4.keyvalue.MultiKey
import org.apache.commons.collections4.map.HashedMap
import org.apache.commons.collections4.map.MultiKeyMap
import java.util.*

/**
 * Primitive cookie store that stores cookies in a (volatile) hash map.
 * Will be sufficient for session cookies.
 */
class MemoryCookieStore: CookieJar {

    /**
     * Stored cookies. The multi-key consists of three parts: name, domain, and path.
     * This ensures that cookies can be overwritten. [RFC 6265 5.3 Storage Model]
     * Not thread-safe!
     */
    private val storage = MultiKeyMap.multiKeyMap(HashedMap<MultiKey<out String>, Cookie>())!!

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(storage) {
            for (cookie in cookies)
                storage.put(cookie.name, cookie.domain, cookie.path, cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = LinkedList<Cookie>()

        synchronized(storage) {
            val iter = storage.mapIterator()
            while (iter.hasNext()) {
                iter.next()
                val cookie = iter.value

                // remove expired cookies
                if (cookie.expiresAt <= System.currentTimeMillis()) {
                    iter.remove()
                    continue
                }

                // add applicable cookies
                if (cookie.matches(url))
                    cookies += cookie
            }
        }

        return cookies
    }

}
