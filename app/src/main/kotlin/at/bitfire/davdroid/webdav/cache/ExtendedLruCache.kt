/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.cache

import android.util.LruCache

/**
 * Simple thread-safe cache class based on [LruCache] that provides atomic [getOrPut]
 * and [getOrPutIfNotNull] methods.
 */
class ExtendedLruCache<K, V>(maxSize: Int) : LruCache<K, V>(maxSize) {

    /**
     * Retrieves data from the cache, if available. Otherwise calls a callback to
     * compute the data and puts it into the cache.
     *
     * @param key       cache key to request
     * @param compute   callback that computes the data for the cache key
     *
     * @return data (from cache in case of a cache hit or generated by [compute] in case of a cache miss)
     */
    @Synchronized
    fun getOrPut(key: K, compute: () -> V): V {
        // use cached value, if possible
        val data = get(key)
        if (data != null)
            return data

        // compute new value otherwise
        val newValue = compute()
        put(key, newValue)
        return newValue
    }

    /**
     * Same as [getOrPut], but allows [key] to be `null`. In this case, the
     * cache will be bypassed and the callback will always be executed.
     */
    @Synchronized
    fun getOrPutIfNotNull(key: K?, compute: () -> V): V =
        if (key == null)
            compute()
        else
            getOrPut(key, compute)

}