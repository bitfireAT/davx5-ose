/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.cache

import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.webdav.HeadResponse
import java.lang.ref.WeakReference

/**
 * Memory cache for HEAD responses. Using a [WebDavDocument.CacheKey] as key guarantees that
 * the cached response won't be used anymore if the ETag changes.
 */
typealias HeadResponseCache = ExtendedLruCache<WebDavDocument.CacheKey, HeadResponse>

object HeadResponseCacheBuilder {

    private const val MAX_ENTRIES = 50

    private var _cache: WeakReference<HeadResponseCache>? = null

    @Synchronized
    fun getInstance(): HeadResponseCache {
        _cache?.get()?.let { return it }
        val newCache = HeadResponseCache(MAX_ENTRIES)
        _cache = WeakReference(newCache)
        return newCache
    }

}