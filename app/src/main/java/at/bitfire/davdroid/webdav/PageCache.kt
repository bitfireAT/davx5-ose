package at.bitfire.davdroid.webdav

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import at.bitfire.davdroid.AndroidSingleton
import at.bitfire.davdroid.log.Logger
import okhttp3.HttpUrl
import org.apache.commons.io.FileUtils
import java.io.File

@WorkerThread
class PageCache(
    context: Context
) {

    companion object: AndroidSingleton<PageCache>() {
        override fun createInstance(context: Context) = PageCache(context)
    }

    data class Key(
        val url: HttpUrl,
        val state: DocumentState,
        val pageIndex: Long
    ) {
        fun asString() = CacheUtils.md5(url, state.asString(), pageIndex)
    }

    val cache: DiskCache


    init {
        val storageManager = ContextCompat.getSystemService(context, StorageManager::class.java)!!
        val cacheDir = File(context.cacheDir, "webdav/page")
        val maxBytes = if (Build.VERSION.SDK_INT >= 26)
            storageManager.getCacheQuotaBytes(storageManager.getUuidForPath(cacheDir)) / 2
        else
            50*FileUtils.ONE_MB
        Logger.log.info("Initializing WebDAV page cache in $cacheDir with ${FileUtils.byteCountToDisplaySize(maxBytes)}")

        cache = DiskCache(cacheDir, maxBytes)
    }

    fun get(key: Key, offset: Long = 0, maxSize: Int = Int.MAX_VALUE, generate: () -> ByteArray?) =
        cache.get(key.asString(), offset, maxSize, generate)

}