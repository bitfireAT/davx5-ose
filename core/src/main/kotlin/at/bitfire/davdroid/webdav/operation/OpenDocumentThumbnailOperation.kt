/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.ThumbnailUtils
import android.net.ConnectivityManager
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.core.content.getSystemService
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.di.scope.IoDispatcher
import at.bitfire.davdroid.webdav.DavHttpClientBuilder
import at.bitfire.davdroid.webdav.cache.ThumbnailCache
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.use

class OpenDocumentThumbnailOperation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val httpClientBuilder: DavHttpClientBuilder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val thumbnailCache: ThumbnailCache
) {

    private val documentDao = db.webDavDocumentDao()

    operator fun invoke(documentId: String, sizeHint: Point, signal: CancellationSignal?): AssetFileDescriptor? {
        logger.info("openDocumentThumbnail documentId=$documentId sizeHint=$sizeHint signal=$signal")

        // don't download the large images just to create a thumbnail on metered networks
        val connectivityManager = context.getSystemService<ConnectivityManager>()!!
        if (connectivityManager.isActiveNetworkMetered)
            return null

        if (signal == null) {
            logger.warning("openDocumentThumbnail without cancellationSignal causes too much problems, please fix calling app")
            return null
        }

        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()

        val docCacheKey = doc.cacheKey()
        if (docCacheKey == null) {
            logger.warning("openDocumentThumbnail won't generate thumbnails when document state (ETag/Last-Modified) is unknown")
            return null
        }

        val thumbFile = thumbnailCache.get(docCacheKey, sizeHint) {
            createThumbnail(doc, sizeHint, signal)
        }

        if (thumbFile != null)
            return AssetFileDescriptor(
                ParcelFileDescriptor.open(thumbFile, ParcelFileDescriptor.MODE_READ_ONLY),
                0, thumbFile.length()
            )

        return null
    }

    private fun createThumbnail(doc: WebDavDocument, sizeHint: Point, signal: CancellationSignal): ByteArray? =
        try {
            runBlocking(ioDispatcher) {
                signal.setOnCancelListener {
                    logger.fine("Cancelling thumbnail generation for #${doc.id}")
                    cancel()        // cancel current coroutine scope
                }

                withTimeout(THUMBNAIL_TIMEOUT_MS) {
                    downloadAndCreateThumbnail(doc, db, sizeHint)
                }
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't generate thumbnail", e)
            null
        }

    private suspend fun downloadAndCreateThumbnail(doc: WebDavDocument, db: AppDatabase, sizeHint: Point): ByteArray? =
        httpClientBuilder
            .buildKtor(doc.mountId, logBody = false)
            .use { httpClient ->
            val url = doc.toKtorUrl(db)
            try {
                httpClient.prepareGet(url) {
                    header(HttpHeaders.Accept, ContentType.Image.Any.toString())
                }.execute { response ->
                    if (response.status.isSuccess()) {
                        val imageStream = response.bodyAsChannel().toInputStream()
                        BitmapFactory.decodeStream(imageStream)?.let { bitmap ->
                            /* Now the whole decoded input bitmap is in memory. This could be improved in the future:
                            1. By writing the input bitmap to a temporary file, and extracting the thumbnail from that file.
                            2. By using a dedicated image loading library it could be possible to only extract potential
                               embedded thumbnails and thus save network traffic. */
                            val thumb = ThumbnailUtils.extractThumbnail(bitmap, sizeHint.x, sizeHint.y)
                            val baos = ByteArrayOutputStream()
                            thumb.compress(Bitmap.CompressFormat.JPEG, 95, baos)
                            return@execute baos.toByteArray()
                        }
                    } else
                        logger.warning("Couldn't download image for thumbnail (${response.status})")
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't download image for thumbnail", e)
            }
            null
        }


    companion object {

        const val THUMBNAIL_TIMEOUT_MS = 15000L

    }

}