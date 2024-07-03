/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.cache

import android.app.Application
import android.graphics.Point
import android.os.Build
import android.os.storage.StorageManager
import android.text.format.Formatter
import androidx.core.content.getSystemService
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.webdav.WebdavScoped
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import javax.inject.Inject

/**
 * Simple disk cache for image thumbnails.
 */
@WebdavScoped
class ThumbnailCache @Inject constructor(context: Application) {

    val storage: DiskCache

    init {
        val storageManager = context.getSystemService<StorageManager>()!!
        val cacheDir = File(context.cacheDir, "webdav/thumbnail")
        val maxBytes = if (Build.VERSION.SDK_INT >= 26)
            storageManager.getCacheQuotaBytes(storageManager.getUuidForPath(cacheDir)) / 2
        else
            50 * 1024*1024  // 50 MB
        Logger.log.info("Initializing WebDAV thumbnail cache with ${Formatter.formatFileSize(context, maxBytes)}")

        storage = DiskCache(cacheDir, maxBytes)
    }


    fun get(docKey: WebDavDocument.CacheKey, sizeHint: Point, generate: () -> ByteArray?): File? {
        val key = Key(docKey, sizeHint)
        return storage.getFileOrPut(key.asString(), generate)
    }

    data class Key(
        val document: WebDavDocument.CacheKey,
        val size: Point
    ) {
        fun asString(): String =
            DigestUtils.sha1Hex("${document.docId} ${document.documentState.eTag} ${document.documentState.lastModified} ${size.x}x${size.y}")
    }

}