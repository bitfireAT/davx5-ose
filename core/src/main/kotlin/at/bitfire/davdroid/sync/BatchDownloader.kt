/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BatchDownloader(
    private val downloadBatch: (List<Url>) -> Unit,
    private val batchSize: Int = 10
) {

    private val mutex = Mutex()
    private val queue = mutableListOf<Url>()

    suspend fun enqueue(url: Url) = mutex.withLock {
        queue += url

        if (batchSize >= queue.size) {

        }
    }

    suspend operator fun plusAssign(url: Url) = enqueue(url)

    suspend fun flush() = downloadBatch()

    private suspend fun downloadBatch() {
        val toDownload = mutex.withLock {
            queue.toList().also {
                queue.clear()
            }
        }

        downloadBatch(toDownload)
    }

}