/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav.cache

import at.bitfire.davdroid.log.Logger

class SegmentedCache<K>(
    val pageSize: Int,
    val loader: PageLoader<K>,
    val backingCache: Cache<SegmentKey<K>>
) {

    fun read(key: K, offset: Long, size: Int, dst: ByteArray): Int {
        var transferred = 0

        var pageIdx = (offset / pageSize).toInt()
        var pageOffset = offset.mod(pageSize)

        while (true) {
            Logger.log.fine("Reading $key from offset: $offset, size: $size")

            // read next chunk
            val data = try {
                val pageKey = SegmentKey(key, pageIdx)
                backingCache.getOrPut(pageKey) {
                    loader.load(pageKey, pageSize)
                }
            } catch (e: IndexOutOfBoundsException) {
                // pageIdx is beyond the last page; return immediately
                break
            }
            Logger.log.fine("Got page $pageIdx with ${data.size} bytes")

            /* Calculate the number of bytes we can actually copy. There are two cases when less
             * than the full page (data.size) has to be copied:
             * 1. At the beginnnig, we may not need the full page (pageOffset != 0).
             * 2. At the end, we need only the requested number of bytes. */
            var usableBytes = data.size - pageOffset
            if (usableBytes > size - transferred)
                usableBytes = size - transferred

            // copy to destination
            System.arraycopy(data, pageOffset, dst, transferred, usableBytes)
            transferred += usableBytes

            /* We have two termination conditions:
             * 1. It was the last page returned by the loader. We can know this by
             *   a) data.size < pageSize, or
             *   b) loader.load throws an IndexOutOfBoundsException.
             * 2. The number of requested bytes to transfer has been reached. */
            if (data.size < pageSize || transferred == size)
                break

            pageIdx++
            pageOffset = 0
        }

        return transferred
    }


    data class SegmentKey<K>(
        val documentKey: K,
        val segment: Int
    )

    interface PageLoader<K> {

        /**
         * Loads the given segment of the document. For instance, this could send a ranged
         * HTTP request and return the result.
         *
         * @param key           document and segment number
         * @param segmentSize   segment size (used to calculate the requested byte position and number of bytes)
         *
         * @return data within the requested range
         * @throws IndexOutOfBoundsException if the requested segment doesn't exist
         */
        fun load(key: SegmentedCache.SegmentKey<K>, segmentSize: Int): ByteArray

    }

}