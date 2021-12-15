/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav.cache

import android.util.LruCache

class MemoryCache<K>(maxMemory: Int): Cache<K> {

    private val storage = object: LruCache<K, ByteArray>(maxMemory) {
        // measure cache size by ByteArray size
        override fun sizeOf(key: K, value: ByteArray) = value.size
    }

    override fun get(key: K): ByteArray? = storage.get(key)

    override fun getOrPut(key: K, generate: () -> ByteArray): ByteArray {
        synchronized(storage) {
            val cached = storage[key]
            if (cached != null)
                return cached

            val data = generate()
            storage.put(key, data)
            return data
        }
    }

}