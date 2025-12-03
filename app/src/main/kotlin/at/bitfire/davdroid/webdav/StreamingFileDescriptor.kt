/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.os.ParcelFileDescriptor
import at.bitfire.dav4jvm.okhttp.DavResource
import at.bitfire.dav4jvm.okhttp.exception.HttpException
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.util.DavUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @param client    HTTP client to use
 */
class StreamingFileDescriptor @AssistedInject constructor(
    @Assisted private val client: OkHttpClient,
    @Assisted private val url: HttpUrl,
    @Assisted private val mimeType: MediaType?,
    @Assisted private val externalScope: CoroutineScope,
    @Assisted private val finishedCallback: OnSuccessCallback,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {

    @AssistedFactory
    interface Factory {
        fun create(client: OkHttpClient, url: HttpUrl, mimeType: MediaType?, externalScope: CoroutineScope, finishedCallback: OnSuccessCallback): StreamingFileDescriptor
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
    private suspend fun downloadNow(writeFd: ParcelFileDescriptor) = runInterruptible(ioDispatcher) {
        dav.get(DavUtils.acceptAnything(preferred = mimeType), null) { response ->
            response.body.use { body ->
                if (response.isSuccessful) {
                    ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { destination ->
                        body.byteStream().use { source ->
                            transferred += source.copyTo(destination)
                        }
                        logger.fine("Downloaded $transferred byte(s) from $url")
                    }

                } else
                    writeFd.closeWithError("${response.code} ${response.message}")
            }
        }
    }

    /**
     * Uploads a WebDAV resource.
     *
     * @param readFd    source file descriptor (could for instance represent a local file)
     */
    private suspend fun uploadNow(readFd: ParcelFileDescriptor) = runInterruptible(ioDispatcher) {
        val body = object: RequestBody() {
            override fun contentType(): MediaType? = mimeType
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                ParcelFileDescriptor.AutoCloseInputStream(readFd).use { input ->
                    transferred += input.copyTo(sink.outputStream())
                    logger.fine("Uploaded $transferred byte(s) to $url")
                }
            }
        }
        DavResource(client, url).put(body) {
            // upload successful
        }
    }


    fun interface OnSuccessCallback {
        fun onFinished(transferred: Long, success: Boolean)
    }

}