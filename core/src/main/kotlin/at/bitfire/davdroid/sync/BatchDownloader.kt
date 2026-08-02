/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collects [Url]s and calls [downloadBatch] once [batchSize] of them have been enqueued
 * (and once more, with the remainder, on [flush]).
 *
 * @param batchSize The number of URLs to accumulate before triggering a batch download. Default is 10.
 * @param downloadBatch The function to call with a batch of URLs to download (typically uses CalDAV/CardDAV multiget).
 */
class BatchDownloader(
    private val batchSize: Int = 10,
    private val downloadBatch: (List<Url>) -> Unit
) {

    /** used to synchronize [queue] access */
    private val mutex = Mutex()

    /** currently enqueued URLs */
    private val queue = mutableListOf<Url>()

    /**
     * Adds a URL to the download queue and triggers a batch download if the queue has reached [batchSize].
     *
     * @param url The URL to be added to the download queue.
     */
    suspend fun enqueue(url: Url) {
        // protect queue operation with lock
        val toDownload: List<Url>? = mutex.withLock {
            // enqueue next download
            queue += url

            // download batch if needed
            if (queue.size >= batchSize)
                queue.clearToList()
            else
                null
        }

        // perform actual download outside the lock
        if (toDownload != null)
            downloadBatch(toDownload)
    }

    /**
     * Downloads all remaining items in the queue as a single batch.
     */
    suspend fun flush() {
        // protect queue operation with lock
        val toDownload = mutex.withLock {
            queue.clearToList()
        }

        // download remaining items as batch outside the lock
        if (toDownload.isNotEmpty())
            downloadBatch(toDownload)
    }


    /**
     * Returns a copy of this list as a new read-only list and clears the original list.
     *
     * Not thread-safe, access must be synchronized by caller.
     *
     * @return A new read-only list containing all elements of the original list.
     */
    private fun <T> MutableList<T>.clearToList(): List<T> =
        toList().also {
            clear()
        }

}
