/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.network.MemoryCookieStore
import at.bitfire.davdroid.settings.Credentials
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject
import javax.inject.Provider

class DavHttpClientBuilder @Inject constructor(
    db: AppDatabase,
    private val httpClientBuilder: Provider<HttpClient.Builder>,
) {

    private val mountDao = db.webDavMountDao()


    /**
     * Creates an HTTP client that can be used to access resources in the given mount.
     *
     * @param mountId    ID of the mount to access
     * @param logBody    whether to log the body of HTTP requests (disable for potentially large files)
     */
    fun build(mountId: Long, logBody: Boolean = true): HttpClient {
        val cookieStore = cookieStores.getOrPut(mountId) {
            MemoryCookieStore()
        }
        val builder = httpClientBuilder.get()
            .loggerInterceptorLevel(if (logBody) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.HEADERS)
            .setCookieStore(cookieStore)

        val credentials = getCredentials(mountId)
        if (credentials != null)
            builder.authenticate(host = null, getCredentials = { credentials })

        return builder.build()
    }

    private fun getCredentials(mountId: Long): Credentials? {
        val mount = runBlocking { mountDao.getById(mountId) }

        return Credentials(
            username = mount.username,
            password = mount.password?.asCharArray(),
            certificateAlias = mount.certificateAlias
            // OAuth is not supported for WebDAV mounts
        )
    }


    companion object {

        /** in-memory cookie stores (one per mount ID) that are available until the content
         * provider (= process) is terminated */
        private val cookieStores = mutableMapOf<Long, CookieJar>()

    }

}