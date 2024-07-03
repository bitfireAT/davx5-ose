/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.cache

import okhttp3.HttpUrl
import java.lang.ref.WeakReference

/**
 * Memory cache for pages of a WebDAV resources (as they're requested by a [at.bitfire.davdroid.webdav.PagingReader.PageLoader]).
 *
 * Allows that multiple pages are kept in memory.
 */
typealias PageCache = ExtendedLruCache<PageCacheBuilder.PageIdentifier, ByteArray>

object PageCacheBuilder {

    const val MAX_PAGE_SIZE = 2 * 1024*1024     // 2 MB
    private const val MAX_ENTRIES = 3   // cache up to 3 pages (6 MB in total) in memory

    private var _cache: WeakReference<PageCache>? = null

    @Synchronized
    fun getInstance(): PageCache {
        _cache?.get()?.let { return it }
        val newCache = PageCache(MAX_ENTRIES)
        _cache = WeakReference(newCache)
        return newCache
    }

    data class PageIdentifier(
        val url: HttpUrl,
        val offset: Long,
        val size: Int
    )

}