/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.cache

import androidx.annotation.VisibleForTesting
import at.bitfire.davdroid.util.KeyedMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
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

        /**
         * Serializes cache-wide structural operations ([clear], [entries], [keys], [trim]) across all
         * [DiskCache] instances (they all share the same lock, regardless of [cacheDir]). These are all
         * fast, blocking-but-brief file system calls – this lock is never held while running a
         * [getFileOrPut] `generate` callback, so a single slow (e.g. network-bound) fetch can't stall
         * unrelated cache reads/writes.
         */
        private val structureMutex = Mutex()

        /**
         * One [Mutex] per cache key, so that concurrent [getFileOrPut] calls for the *same* key are
         * serialized (preventing torn writes to the same file), while calls for different keys – even
         * while one of them is stuck in a slow `generate` callback – proceed independently instead of
         * stalling behind a single global lock.
         */
        private val keyMutex = KeyedMutex()
    }

    private val logger = Logger.getGlobal()
    private val writeCounter = AtomicInteger()

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
    suspend fun getFileOrPut(key: String, generate: () -> ByteArray?): File? = keyMutex.withLock(key) {
        withContext(Dispatchers.IO) {
            val file = File(cacheDir, key)
            if (file.exists()) {
                logger.fine("Cache hit: $key")
                return@withContext file
            } else {
                logger.fine("Cache miss: $key → generating")
                val result = generate() ?: return@withContext null

                file.outputStream().use { output ->
                    output.write(result)
                }

                if (writeCounter.getAndIncrement().mod(CLEANUP_RATE) == 0) trim()

                return@withContext file
            }
        }
    }


    suspend fun clear() = structureMutex.withLock {
        withContext(Dispatchers.IO) {
            cacheDir.listFiles()?.forEach { entry ->
                entry.delete()
            }
        }
    }

    suspend fun entries(): Int = structureMutex.withLock {
        withContext(Dispatchers.IO) {
            cacheDir.listFiles()!!.size
        }
    }

    suspend fun keys(): Array<String> = structureMutex.withLock {
        withContext(Dispatchers.IO) {
            cacheDir.list()!!
        }
    }

    /**
     * Trims the cache to keep it smaller than [maxSize].
     */
    @VisibleForTesting
    internal suspend fun trim(): Int = structureMutex.withLock {
        withContext(Dispatchers.IO) {
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
            removed
        }
    }

}
