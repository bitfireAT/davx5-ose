/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BatchDownloader(
    private val batchSize: Int = 10,
    private val downloadBatch: (List<Url>) -> Unit
) {

    private val mutex = Mutex()
    private val queue = mutableListOf<Url>()

    suspend fun enqueue(url: Url) {
        // protect queue operation with lock
        val toDownload: List<Url>? = mutex.withLock {
            // enqueue next download
            queue += url

            // download batch if needed
            if (queue.size >= batchSize)
                queue.toList().also {
                    queue.clear()
                }
            else
                null
        }

        // download batch queue became too large
        if (toDownload != null)
            downloadBatch(toDownload)
    }

    suspend operator fun plusAssign(url: Url) = enqueue(url)

    suspend fun flush() {
        // protect queue operation with lock
        val toDownload = mutex.withLock {
            queue.toList().also {
                queue.clear()
            }
        }

        // download remaining items as batch
        downloadBatch(toDownload)
    }

}
