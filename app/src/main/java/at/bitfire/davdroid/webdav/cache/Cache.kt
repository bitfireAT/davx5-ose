package at.bitfire.davdroid.webdav.cache

interface Cache<K> {

    fun get(key: K): ByteArray?
    fun getOrPut(key: K, generate: () -> ByteArray): ByteArray

}