/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.cache

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.os.storage.StorageManager
import androidx.core.content.getSystemService
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.log.Logger
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * Simple disk cache for image thumbnails.
 */
class ThumbnailCache private constructor(context: Context) {

    companion object {

        private var _instance: ThumbnailCache? = null

        @Synchronized
        fun getInstance(context: Context): ThumbnailCache {
            _instance?.let { return it }

            val newInstance = ThumbnailCache(context)
            _instance = newInstance
            return newInstance
        }

    }

    val storage: DiskCache

    init {
        val storageManager = context.getSystemService<StorageManager>()!!
        val cacheDir = File(context.cacheDir, "webdav/thumbnail")
        val maxBytes = if (Build.VERSION.SDK_INT >= 26)
            storageManager.getCacheQuotaBytes(storageManager.getUuidForPath(cacheDir)) / 2
        else
            50*FileUtils.ONE_MB
        Logger.log.info("Initializing WebDAV thumbnail cache with ${FileUtils.byteCountToDisplaySize(maxBytes)}")

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