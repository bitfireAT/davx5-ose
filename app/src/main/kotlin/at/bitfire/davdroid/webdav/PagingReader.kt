/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import java.io.IOException
import java.util.logging.Logger
import kotlin.math.min

/**
 * Splits a resource into pages (segments) so that read accesses can be cached per page.
 *
 * For instance, if [fileSize] is 3 MB and [pageSize] is 2 MB, multiple read requests within the
 * first 2 MB will cause only the first page (0 – 2 MB) to be loaded once and then fulfilled
 * from within the cache. For requests between 2 MB and 3 MB, the second page (and in this case last)
 * is loaded and used.
 *
 * @param fileSize  file size (must not change between read operations)
 * @param pageSize  page size (big enough to cache efficiently, small enough to avoid unnecessary traffic and spare memory)
 * @param loader    loads page content from the actual data source
 */
@Suppress("LocalVariableName")
class PagingReader(
    private val fileSize: Long,
    private val pageSize: Int,
    private val loader: PageLoader
) {

    val logger: Logger = Logger.getLogger(javaClass.name)

    /**
     * Interface for loading a page from the data source (like a local file
     * or a WebDAV resource).
     */
    fun interface PageLoader {
        /**
         * Loads the requested data from the data source. For instance this could cause
         *
         * - a file seek + read or
         * - a ranged WebDAV request.
         *
         * @param offset    position to start
         * @param size      number of bytes to load
         *
         * @return array with bytes fetched from data source
         */
        fun loadPage(offset: Long, size: Int): ByteArray
    }

    /**
     * Represents a loaded page (meta information + data).
     */
    class CachedPage(
        val idx: Long,
        val start: Long,
        val end: Long,
        val data: ByteArray
    )

    /** currently loaded page */
    private var currentPage: CachedPage? = null

    /**
     * Reads a given number of bytes from a given position.
     *
     * Will split the request into multiple page access operations, if necessary.
     *
     * @param offset    starting position
     * @param _size     number of bytes to read
     * @param dst       destination where data are read into
     *
     * @return number of bytes read (may be smaller than [_size] if the file is not that big)
     */
    fun read(offset: Long, _size: Int, dst: ByteArray): Int {
        // input validation
        if (offset > fileSize)
            throw IndexOutOfBoundsException()
        var remaining = min(_size.toLong(), fileSize - offset).toInt()

        var transferred = 0
        while (remaining > 0) {
            val nrBytes = readPage(offset + transferred, remaining, dst, transferred)
            if (nrBytes == 0)   // EOF
                break
            transferred += nrBytes
            remaining -= nrBytes
        }

        return transferred
    }

    /**
     * Tries to read a given number of bytes from a given position, but stays
     * within one page – it will not read across two pages.
     *
     * This method will determine the page that contains [position] and read only
     * from this page.
     *
     * @param position      starting position
     * @param size          number of bytes requested
     * @param dst           destination where data are read into
     * @param dstOffset     starting offset within destination array
     *
     * @return number of bytes read (may be less than [size] when the page ends before);
     * 0 guarantees that there are no more bytes (EOF)
    */
    @Synchronized
    fun readPage(position: Long, size: Int, dst: ByteArray, dstOffset: Int): Int {
        logger.fine("read(position=$position, size=$size, dstOffset=$dstOffset)")

        // read max. 1 page
        val pgIdx = position / pageSize
        val page = currentPage?.takeIf { it.idx == pgIdx } ?: run {
            val pgStart = pgIdx * pageSize
            val pgEnd = min((pgIdx + 1) * pageSize, fileSize)
            val pgSize = (pgEnd - pgStart).toInt()

            val pageData =
                if (pgSize == 0)
                    ByteArray(0)        // don't load 0-byte pages
                else
                    loader.loadPage(pgStart, pgSize)
            if (pageData.size != pgSize)
                throw IOException("Couldn't fetch whole file segment (expected $pgSize bytes, got ${pageData.size} bytes)")

            val newPage = CachedPage(pgIdx, pgStart, pgEnd, pageData)
            currentPage = newPage
            newPage
        }

        val pgSize = (page.end - page.start).toInt()
        logger.fine("pgIdx=${page.idx}, pgStart=${page.start}, pgEnd=${page.end}, pgSize=$pgSize")

        val inPageStart = (position - page.start).toInt()
        val len = min(pgSize - inPageStart, size)       // use the remaining number of bytes in the page, or less if less were requested
        logger.fine("inPageStart=$inPageStart, len=$len")

        System.arraycopy(page.data, inPageStart, dst, dstOffset, len)

        return len
    }

}