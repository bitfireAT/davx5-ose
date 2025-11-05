/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.RequiresApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient

/**
 * Use this wrapper to ensure that all memory is released as soon as [onRelease] is called.
 *
 * - (2021/12/02) Currently Android's `StorageManager.openProxyFileDescriptor` has a memory leak:
 * the given callback is registered in `com.android.internal.os.AppFuseMount` (which adds it to
 * a [Map]), but is not unregistered anymore. So it stays in the memory until the whole mount
 * is unloaded. See https://issuetracker.google.com/issues/208788568.
 * - (2024/08/24) [Fixed in Android.](https://android.googlesource.com/platform/frameworks/base/+/e7dbf78143ba083af7a8ecadd839a9dbf6f01655%5E%21/#F0)
 *
 * **All fields of objects of this class must be set to `null` when [onRelease] is called!**
 * Otherwise they will leak memory.
 *
 * @param httpClient    HTTP client ([RandomAccessCallbackWrapper] is responsible to close it)
 */
@RequiresApi(26)
class RandomAccessCallbackWrapper @AssistedInject constructor(
    @Assisted httpClient: OkHttpClient,
    @Assisted url: HttpUrl,
    @Assisted mimeType: MediaType?,
    @Assisted headResponse: HeadResponse,
    @Assisted externalScope: CoroutineScope,
    callbackFactory: RandomAccessCallback.Factory
): ProxyFileDescriptorCallback() {

    @AssistedFactory
    interface Factory {
        fun create(httpClient: OkHttpClient, url: HttpUrl, mimeType: MediaType?, headResponse: HeadResponse, externalScope: CoroutineScope): RandomAccessCallbackWrapper
    }


    // callback reference

    /**
     * This field is initialized with a strong reference to the callback. It is cleared when
     * [onRelease] is called so that the garbage collector can remove the actual [RandomAccessCallback].
     */
    private var callbackRef: RandomAccessCallback? =
        callbackFactory.create(httpClient, url, mimeType, headResponse, externalScope)

    private fun requireCallback(functionName: String): RandomAccessCallback =
        callbackRef ?: throw ErrnoException(functionName, OsConstants.EBADF)


    // non-interface delegates

    fun fileDescriptor() =
        requireCallback("fileDescriptor").fileDescriptor()


    // delegating implementation of ProxyFileDescriptorCallback

    override fun onFsync() { /* not used */ }

    override fun onGetSize() =
        requireCallback("onGetSize").onGetSize()

    override fun onRead(offset: Long, size: Int, data: ByteArray) =
        requireCallback("onRead").onRead(offset, size, data)

    override fun onWrite(offset: Long, size: Int, data: ByteArray) =
        requireCallback("onWrite").onWrite(offset, size, data)

    override fun onRelease() {
        requireCallback("onRelease").onRelease()

        // remove reference to allow garbage collection
        callbackRef = null
    }

}