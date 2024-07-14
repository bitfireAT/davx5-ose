/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.cache

import java.io.File
import java.util.logging.Logger

/**
 * Disk-based cache that maps [String]s to [ByteArray]s.
 *
 * @param cacheDir  directory where to put cache files
 * @param maxSize   max. total cache size (approximately, may be exceeded for some time)
 */
class DiskCache(
    val cacheDir: File,
    val maxSize: Long
) {

    companion object {
        /**
         * after how many cache writes [trim] is called
         */
        const val CLEANUP_RATE = 15
    }

    private val logger = Logger.getGlobal()
    private var writeCounter: Int = 0

    init {
        if (!cacheDir.isDirectory)
            if (!cacheDir.mkdirs())
                throw IllegalArgumentException("Couldn't create cache in $cacheDir")
    }

    /**
     * Gets the file that contains the given key. If the key is not in the cache, the value is being generated from the
     * callback, stored in the cache and the backing file is returned.
     *
     * It's not guaranteed that the file still exists when you're using it! For instance, it may have already
     * been removed to keep the cache in size.
     *
     * @param key      key of the cached entry
     * @param generate callback that generates the value
     *
     * @return the file that contains the value
     */
    fun getFileOrPut(key: String, generate: () -> ByteArray?): File? {
        synchronized(this) {
            val file = File(cacheDir, key)
            if (file.exists()) {
                logger.fine("Cache hit: $key")
                return file
            } else {
                logger.fine("Cache miss: $key → generating")
                val result = generate() ?: return null

                file.outputStream().use { output ->
                    output.write(result)
                }

                if (writeCounter++.mod(CLEANUP_RATE) == 0)
                    trim()

                return file
            }
        }
    }


    @Synchronized
    fun clear() {
        cacheDir.listFiles()?.forEach { entry ->
            entry.delete()
        }
    }

    @Synchronized
    fun entries(): Int {
        return cacheDir.listFiles()!!.size
    }

    fun keys(): Array<String> = cacheDir.list()!!

    /**
     * Trims the cache to keep it smaller than [maxSize].
     */
    @Synchronized
    fun trim(): Int {
        var removed = 0
        logger.fine("Trimming disk cache to $maxSize bytes")

        val files = cacheDir.listFiles()!!.toMutableList()
        files.sortBy { file -> file.lastModified() }    // sort by modification time (ascending)

        while (files.sumOf { file -> file.length() } > maxSize) {
            val file = files.removeFirst()      // take first (= oldest) file
            logger.finer("Removing $file")
            file.delete()
            removed++
        }
        return removed
    }

}