/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.cache

import androidx.annotation.VisibleForTesting
import at.bitfire.davdroid.webdav.cache.DiskCache.Companion.fileMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

        private val fileMutex = Mutex()
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
    suspend fun getFileOrPut(key: String, generate: () -> ByteArray?): File? = fileMutex.withLock {
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

            if (writeCounter++.mod(CLEANUP_RATE) == 0) withContext(Dispatchers.IO) {
                trim()
            }

            return file
        }
    }


    suspend fun clear() = fileMutex.withLock {
        cacheDir.listFiles()?.forEach { entry ->
            entry.delete()
        }
    }

    suspend fun entries(): Int = fileMutex.withLock {
        cacheDir.listFiles()!!.size
    }

    suspend fun keys(): Array<String> = fileMutex.withLock {
        cacheDir.list()!!
    }

    /**
     * Trims the cache to keep it smaller than [maxSize].
     *
     * Doesn't hold [fileMutex], it should be held by the calling function.
     */
    @VisibleForTesting
    internal fun trim(): Int {
        var removed = 0
        logger.fine("Trimming disk cache to $maxSize bytes")

        val files = cacheDir.listFiles()!!.toMutableList()
        files.sortBy { file -> file.lastModified() }    // sort by modification time (ascending)

        while (files.sumOf { file -> file.length() } > maxSize) {
            val file = files.removeAt(0)      // take first (= oldest) file
            logger.finer("Removing $file")
            file.delete()
            removed++
        }
        return removed
    }

}