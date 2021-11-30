package at.bitfire.davdroid.webdav.cache

import java.io.InputStream

interface Cache<K> {

    fun get(key: K): ByteArray?
    fun getOrPut(key: K, generate: () -> ByteArray): ByteArray

}