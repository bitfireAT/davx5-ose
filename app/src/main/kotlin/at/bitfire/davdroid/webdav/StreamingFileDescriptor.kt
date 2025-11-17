/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.os.ParcelFileDescriptor
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.util.DavUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @param client    HTTP client to use
 */
class StreamingFileDescriptor @AssistedInject constructor(
    @Assisted private val client: HttpClient,
    @Assisted private val url: Url,
    @Assisted private val mimeType: ContentType?,
    @Assisted private val externalScope: CoroutineScope,
    @Assisted private val finishedCallback: OnSuccessCallback,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {

    @AssistedFactory
    interface Factory {
        fun create(client: HttpClient, url: Url, mimeType: ContentType?, externalScope: CoroutineScope, finishedCallback: OnSuccessCallback): StreamingFileDescriptor
    }

    val dav = DavResource(client, url)
    var transferred: Long = 0

    fun download() = doStreaming(false)
    fun upload() = doStreaming(true)

    private fun doStreaming(upload: Boolean): ParcelFileDescriptor {
        val (readFd, writeFd) = ParcelFileDescriptor.createReliablePipe()

        var success = false
        externalScope.launch {
            try {
                if (upload)
                    uploadNow(readFd)
                else
                    downloadNow(writeFd)

                success = true
            } catch (e: HttpException) {
                logger.log(Level.WARNING, "HTTP error when opening remote file", e)
                writeFd.closeWithError("${e.statusCode} ${e.message}")
            } catch (e: Exception) {
                logger.log(Level.INFO, "Couldn't serve file (not necessarily an error)", e)
                writeFd.closeWithError(e.message)
            } finally {
                // close pipe
                try {
                    readFd.close()
                    writeFd.close()
                } catch (_: IOException) {}

                finishedCallback.onFinished(transferred, success)
            }
        }

        return if (upload)
            writeFd
        else
            readFd
    }

    /**
     * Downloads a WebDAV resource.
     *
     * @param writeFd   destination file descriptor (could for instance represent a local file)
     */
    private suspend fun downloadNow(writeFd: ParcelFileDescriptor) = withContext(ioDispatcher) {
        dav.get(DavUtils.acceptAnything(preferred = mimeType), null) { response ->
            if (response.status.isSuccess())
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { destination ->
                    response.bodyAsChannel().copyTo(destination)
                }
            else
                writeFd.closeWithError(response.status.toString())
        }
    }

    /**
     * Uploads a WebDAV resource.
     *
     * @param readFd    source file descriptor (could for instance represent a local file)
     */
    private suspend fun uploadNow(readFd: ParcelFileDescriptor) = withContext(ioDispatcher) {
        ParcelFileDescriptor.AutoCloseInputStream(readFd).use { stream ->
            DavResource(client, url).put(
                stream.toByteReadChannel(),
                mimeType ?: ContentType.Application.OctetStream
            ) {
                // upload successful
            }
        }
    }


    fun interface OnSuccessCallback {
        fun onFinished(transferred: Long, success: Boolean)
    }

}