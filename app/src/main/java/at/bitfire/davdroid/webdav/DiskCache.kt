package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.log.Logger
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Disk-based cache that maps [String]s to [ByteArray]s.
 */
class DiskCache(
    val cacheDir: File,
    val maxSize: Long
) {

    companion object {
        /**
         * after how many cache writes [trim] is called
         */
        const val CLEANUP_RATE = 10
    }

    private val globalLock = Any()
    private val writeCounter = AtomicInteger()


    init {
        if (!cacheDir.isDirectory)
            if (!cacheDir.mkdirs())
                throw IllegalArgumentException("Couldn't create cache in $cacheDir")

        trim()
    }


    fun get(key: String, generate: () -> ByteArray?): ByteArray? {
        synchronized(globalLock) {
            val file = File(cacheDir, key)
            if (file.exists()) {
                // cache hit
                val result = file.inputStream().use { input ->
                    IOUtils.toByteArray(input)
                }

                if (writeCounter.incrementAndGet().mod(CLEANUP_RATE) == 0)
                    trim()

                return result
            } else {
                // file does't exist yet; cache miss
                val result = generate() ?: return null
                file.outputStream().use { output ->
                    IOUtils.write(result, output)
                }
                return result
            }
        }
    }

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
                    IOUtils.write(result, output)
                }

                if (writeCounter.incrementAndGet().mod(CLEANUP_RATE) == 0)
                    trim()

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