/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.content.Context
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.ui.NotificationRegistry
import at.bitfire.davdroid.util.DavUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @param client    HTTP client– [StreamingFileDescriptor] is responsible to close it
 */
class StreamingFileDescriptor @AssistedInject constructor(
    @Assisted private val client: HttpClient,
    @Assisted private val url: HttpUrl,
    @Assisted private val mimeType: MediaType?,
    @Assisted private val externalScope: CoroutineScope,
    @Assisted private val finishedCallback: OnSuccessCallback,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val notificationRegistry: NotificationRegistry
) {

    companion object {
        /** 1 MB transfer buffer */
        private const val BUFFER_SIZE = 1024*1024
    }

    @AssistedFactory
    interface Factory {
        fun create(client: HttpClient, url: HttpUrl, mimeType: MediaType?, externalScope: CoroutineScope, finishedCallback: OnSuccessCallback): StreamingFileDescriptor
    }

    val dav = DavResource(client.okHttpClient, url)
    var transferred: Long = 0

    private val notificationManager = NotificationManagerCompat.from(context)
    private val notification = NotificationCompat.Builder(context, notificationRegistry.CHANNEL_STATUS)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setContentText(dav.fileName())
        .setSmallIcon(R.drawable.ic_storage_notify)
        .setOngoing(true)
    val notificationTag = url.toString()


    fun download() = doStreaming(false)
    fun upload() = doStreaming(true)

    private fun doStreaming(upload: Boolean): ParcelFileDescriptor {
        val (readFd, writeFd) = ParcelFileDescriptor.createReliablePipe()

        externalScope.launch(ioDispatcher) {
            try {
                if (upload)
                    uploadNow(readFd)
                else
                    downloadNow(writeFd)
            } catch (e: HttpException) {
                logger.log(Level.WARNING, "HTTP error when opening remote file", e)
                writeFd.closeWithError("${e.code} ${e.message}")
            } catch (e: Exception) {
                logger.log(Level.INFO, "Couldn't serve file (not necessarily an error)", e)
                writeFd.closeWithError(e.message)
            } finally {
                client.close()
            }

            try {
                readFd.close()
                writeFd.close()
            } catch (_: IOException) {}

            notificationManager.cancel(notificationTag, NotificationRegistry.NOTIFY_WEBDAV_ACCESS)

            finishedCallback.onSuccess(transferred)
        }

        return if (upload)
            writeFd
        else
            readFd
    }

    @WorkerThread
    private suspend fun downloadNow(writeFd: ParcelFileDescriptor) = runInterruptible {
        dav.get(DavUtils.acceptAnything(preferred = mimeType), null) { response ->
            response.body.use { body ->
                if (response.isSuccessful) {
                    val length = body.contentLength()

                    notification.setContentTitle(context.getString(R.string.webdav_notification_download))
                    if (length == -1L)
                        // unknown file size, show notification now (no updates on progress)
                        notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_WEBDAV_ACCESS, notificationTag) {
                            notification
                                .setProgress(100, 0, true)
                                .build()
                        }
                    else
                        // known file size
                        notification.setSubText(Formatter.formatFileSize(context, length))

                    ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        body.byteStream().use { source ->
                            // read first chunk
                            var bytes = source.read(buffer)
                            while (bytes != -1) {
                                // update notification (if file size is known)
                                if (length > 0)
                                    notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_WEBDAV_ACCESS, notificationTag) {
                                        val progress = (transferred*100/length).toInt()
                                        notification
                                            .setProgress(100, progress, false)
                                            .build()
                                    }

                                // write chunk
                                output.write(buffer, 0, bytes)
                                transferred += bytes

                                // read next chunk
                                bytes = source.read(buffer)
                            }
                            logger.finer("Downloaded $transferred byte(s) from $url")
                        }
                    }

                } else
                    writeFd.closeWithError("${response.code} ${response.message}")
            }
        }
    }

    @WorkerThread
    private suspend fun uploadNow(readFd: ParcelFileDescriptor) = runInterruptible {
        val body = object: RequestBody() {
            override fun contentType(): MediaType? = mimeType
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_WEBDAV_ACCESS, notificationTag) {
                    notification
                        .setContentTitle(context.getString(R.string.webdav_notification_upload))
                        .build()
                }

                ParcelFileDescriptor.AutoCloseInputStream(readFd).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)

                    // read first chunk
                    var size = input.read(buffer)
                    while (size != -1) {
                        // write chunk
                        sink.write(buffer, 0, size)
                        transferred += size

                        // read next chunk
                        size = input.read(buffer)
                    }
                    logger.finer("Uploaded $transferred byte(s) to $url")
                }
            }
        }
        DavResource(client.okHttpClient, url).put(body) {
            // upload successful
        }
    }


    fun interface OnSuccessCallback {
        fun onSuccess(transferred: Long)
    }

}