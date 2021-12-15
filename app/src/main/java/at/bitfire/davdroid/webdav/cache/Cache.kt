/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav.cache

interface Cache<K> {

    fun get(key: K): ByteArray?
    fun getOrPut(key: K, generate: () -> ByteArray): ByteArray

}