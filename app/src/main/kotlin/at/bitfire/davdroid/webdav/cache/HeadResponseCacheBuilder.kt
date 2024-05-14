/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.cache

import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.webdav.HeadResponse
import at.bitfire.davdroid.webdav.WebdavComponent
import at.bitfire.davdroid.webdav.WebdavScoped
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn

/**
 * Memory cache for HEAD responses. Using a [WebDavDocument.CacheKey] as key guarantees that
 * the cached response won't be used anymore if the ETag changes.
 */
typealias HeadResponseCache = ExtendedLruCache<WebDavDocument.CacheKey, HeadResponse>

@Module
@InstallIn(WebdavComponent::class)
object HeadResponseCacheModule {
    @Provides
    @WebdavScoped
    fun headResponseCache(): HeadResponseCache = HeadResponseCache(
        maxSize = 50        // max entries
    )
}