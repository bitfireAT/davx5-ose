/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.content.Context
import android.provider.DocumentsContract
import androidx.annotation.VisibleForTesting
import at.bitfire.dav4jvm.okhttp.DavResource
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.settings.Credentials
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Provider

class WebDavMountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val httpClientBuilder: Provider<HttpClientBuilder>
) {

    private val mountDao = db.webDavMountDao()
    private val documentDao = db.webDavDocumentDao()

    /** authority of our WebDAV document provider ([DavDocumentsProvider]) */
    private val authority = context.getString(R.string.webdav_authority)

    /**
     * Checks whether an HTTP endpoint supports WebDAV and if it does, adds it as a new WebDAV mount.
     *
     * @param url         URL of the HTTP endpoint
     * @param displayName display name of the mount
     * @param credentials credentials to use for the mount
     *
     * @return `true` if the mount was added successfully, `false` if the endpoint doesn't support WebDAV
     */
    suspend fun addMount(
        url: HttpUrl,
        displayName: String,
        credentials: Credentials?
    ): Boolean {
        val webdavUrl = hasWebDav(url, credentials)
        if (webdavUrl == null)
            return false

        // create in database
        val mount = WebDavMount(
            url = webdavUrl,
            name = displayName
        )
        val id = db.webDavMountDao().insert(mount)

        // store credentials
        val credentialsStore = CredentialsStore(context)
        credentialsStore.setCredentials(id, credentials)

        // notify content URI listeners
        DocumentProviderUtils.notifyMountsChanged(context)

        return true
    }

    suspend fun delete(mount: WebDavMount) {
        // remove mount from database
        mountDao.deleteAsync(mount)

        // remove credentials, too
        CredentialsStore(context).setCredentials(mount.id, null)

        // notify content URI listeners
        DocumentProviderUtils.notifyMountsChanged(context)
    }

    fun getAllFlow() = mountDao.getAllFlow()

    fun getAllWithRootFlow() = mountDao.getAllWithQuotaFlow()

    suspend fun refreshAllQuota() {
        val resolver = context.contentResolver

        withContext(ioDispatcher) {
            // query root document of each mount to refresh quota
            mountDao.getAll().forEach { mount ->
                documentDao.getOrCreateRoot(mount).let { root ->
                    var loading = true
                    while (loading) {
                        val rootDocumentUri = DocumentsContract.buildChildDocumentsUri(authority, root.id.toString())
                        resolver.query(rootDocumentUri, null, null, null, null)?.use { cursor ->
                            loading = cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING)
                        }

                        if (loading)        // still loading, wait a bit
                            delay(100)
                    }
                }
            }
        }
    }


    // helpers

    /**
     * Checks whether WebDAV is supported at given URL with given credentials
     * and returns the resulting if following a few redirects.
     *
     * @param url The URL to check
     * @param credentials The credentials to use for the request
     * @return The URL at which WebDAV support was found
     */
    @VisibleForTesting
    internal suspend fun hasWebDav(
        url: HttpUrl,
        credentials: Credentials?
    ): HttpUrl? = withContext(ioDispatcher) {
        val validVersions = arrayOf("1", "2", "3")

        val builder = httpClientBuilder.get()
        if (credentials != null)
            builder.authenticate(
                domain = null,
                getCredentials = { credentials }
            )
        val httpClient = builder.build()

        var webdavUrl: HttpUrl? = null
        val dav = DavResource(httpClient, url)
        runInterruptible {
            dav.options(followRedirects = true) { davCapabilities, response ->
                if (davCapabilities.any { it in validVersions })
                    webdavUrl = dav.location
            }
        }

        webdavUrl
    }

}