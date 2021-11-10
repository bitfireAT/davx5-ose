package at.bitfire.davdroid.webdav

import androidx.annotation.WorkerThread
import at.bitfire.davdroid.log.Logger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import kotlin.math.min

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

    private val globalLock = Any()
    private val writeCounter = AtomicInteger()
    private val workerScope = CoroutineScope(Dispatchers.IO + CoroutineName("DiskCache cleanup"))


    init {
        if (!cacheDir.isDirectory)
            if (!cacheDir.mkdirs())
                throw IllegalArgumentException("Couldn't create cache in $cacheDir")

        trim()
    }


    /**
     * Gets the cached value with the given key. If the key is not in the cache, the value is being generated from the
     * callback, stored in the cache and returned.
     *
     * @param key      key of the cached entry
     * @param offset   used if only a part of the value is required
     * @param maxSize  used if only a part of the value is required
     * @param generate callback that generates the whole value (not only the part given by [offset] and [maxSize]!)
     *
     * @return the value (either taken from the cache or from [generate]), limited [offset]/[maxSize]
     */
    fun get(key: String, offset: Long = 0, maxSize: Int = Int.MAX_VALUE, generate: () -> ByteArray?): ByteArray? {
        synchronized(globalLock) {
            val file = File(cacheDir, key)
            if (file.exists()) {
                // cache hit
                file.inputStream().use { input ->
                    if (offset != 0L)
                        if (input.skip(offset) != offset)
                            throw IllegalStateException("Couldn't skip first $offset bytes of $file")

                    val size = min(
                        maxSize.toLong(),
                        file.length() - offset
                    ).toInt()
                    val buffer = ByteArray(size)
                    input.read(buffer)
                    return buffer
                }
            } else {
                // file does't exist yet; cache miss
                val result = generate() ?: return null

                file.outputStream().use { output ->
                    try {
                        output.write(result)
                    } catch (e: IOException) {
                        // write error; disk full?
                        Logger.log.log(Level.WARNING, "Couldn't write cache entry $key", e)
                        file.delete()
                    }
                }

                if (writeCounter.incrementAndGet().mod(CLEANUP_RATE) == 0)
                    workerScope.launch {
                        trim()
                    }

                if (maxSize != -1)
                    return result.copyOfRange(offset.toInt(), min(offset.toInt() + maxSize, result.size))
                else
                    return result
            }
        }
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
    fun getFile(key: String, generate: () -> ByteArray?): File? {
        synchronized(globalLock) {
            val file = File(cacheDir, key)
            if (file.exists()) {
                // cache HIT
                return file
            } else {
                // file does't exist yet; cache MISS
                val result = generate() ?: return null

                file.outputStream().use { output ->
                    output.write(result)
                }

                if (writeCounter.incrementAndGet().mod(CLEANUP_RATE) == 0)
                    workerScope.launch {
                        trim()
                    }

                return file
            }
        }
    }


    fun clear() {
        synchronized(globalLock) {
            FileUtils.cleanDirectory(cacheDir)
        }
    }

    fun entries(): Int {
        synchronized(globalLock) {
            return cacheDir.listFiles()!!.size
        }
    }

    fun keys(): Array<String> = cacheDir.list()!!

    /**
     * Trims the cache to keep it smaller than [maxSize].
     */
    @WorkerThread
    fun trim(): Int {
        var removed = 0
        synchronized(globalLock) {
            Logger.log.fine("Trimming disk cache to $maxSize bytes")

            val files = cacheDir.listFiles()!!.toMutableList()
            files.sortBy { file -> file.lastModified() }    // sort by modification time (ascending)

            while (files.sumOf { file -> file.length() } > maxSize) {
                val file = files.removeFirst()      // take first (= oldest) file
                Logger.log.finer("Removing $file")
                file.delete()
                removed++
            }
        }
        return removed
    }

}