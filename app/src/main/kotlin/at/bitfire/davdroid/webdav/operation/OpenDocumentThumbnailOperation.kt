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
import at.bitfire.dav4jvm.DavResource
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.webdav.cache.ThumbnailCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.use

class OpenDocumentThumbnailOperation @Inject constructor(
    private val actor: DavDocumentsActor,
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
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
        val accessScope = CoroutineScope(SupervisorJob())
        signal.setOnCancelListener {
            logger.fine("Cancelling thumbnail generation for $documentId")
            accessScope.cancel()
        }

        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()

        val docCacheKey = doc.cacheKey()
        if (docCacheKey == null) {
            logger.warning("openDocumentThumbnail won't generate thumbnails when document state (ETag/Last-Modified) is unknown")
            return null
        }

        val thumbFile = thumbnailCache.get(docCacheKey, sizeHint) {
            // create thumbnail
            val job = accessScope.async {
                withTimeout(THUMBNAIL_TIMEOUT_MS) {
                    actor.httpClient(doc.mountId, logBody = false).use { client ->
                        val url = doc.toHttpUrl(db)
                        val dav = DavResource(client.okHttpClient, url)
                        var result: ByteArray? = null
                        runInterruptible(ioDispatcher) {
                            dav.get("image/*", null) { response ->
                                response.body.byteStream().use { data ->
                                    BitmapFactory.decodeStream(data)?.let { bitmap ->
                                        val thumb = ThumbnailUtils.extractThumbnail(bitmap, sizeHint.x, sizeHint.y)
                                        val baos = ByteArrayOutputStream()
                                        thumb.compress(Bitmap.CompressFormat.JPEG, 95, baos)
                                        result = baos.toByteArray()
                                    }
                                }
                            }
                        }
                        result
                    }
                }
            }

            try {
                runBlocking {
                    job.await()
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't generate thumbnail", e)
                null
            }
        }

        if (thumbFile != null)
            return AssetFileDescriptor(
                ParcelFileDescriptor.open(thumbFile, ParcelFileDescriptor.MODE_READ_ONLY),
                0, thumbFile.length()
            )

        return null
    }


    companion object {

        const val THUMBNAIL_TIMEOUT_MS = 15000L

    }

}