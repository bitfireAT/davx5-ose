/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.content.Context
import android.provider.DocumentsContract
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.settings.Credentials
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

class WebDavMountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val webDavUrlChecker: WebDavUrlChecker
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
        url: Url,
        displayName: String,
        credentials: Credentials?
    ): Boolean {
        val webdavUrl = webDavUrlChecker.getWebDavUrl(url, credentials) ?: return false

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
}
