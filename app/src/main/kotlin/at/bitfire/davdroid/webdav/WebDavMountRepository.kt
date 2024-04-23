package at.bitfire.davdroid.webdav

import android.app.Application
import at.bitfire.dav4jvm.DavResource
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import org.apache.commons.collections4.CollectionUtils
import javax.inject.Inject

class WebDavMountRepository @Inject constructor(
    val context: Application,
    val db: AppDatabase
) {

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
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasWebDav(url, credentials))
            return@withContext false

        // create in database
        val mount = WebDavMount(
            url = url,
            name = displayName
        )
        val id = db.webDavMountDao().insert(mount)

        // store credentials
        val credentialsStore = CredentialsStore(context)
        credentialsStore.setCredentials(id, credentials)

        // notify content URI listeners
        DavDocumentsProvider.notifyMountsChanged(context)

        true
    }

    private suspend fun hasWebDav(
        url: HttpUrl,
        credentials: Credentials?
    ): Boolean = withContext(Dispatchers.IO) {
        var supported = false

        HttpClient.Builder(context, null, credentials)
            .setForeground(true)
            .build()
            .use { client ->
                val dav = DavResource(client.okHttpClient, url)
                runInterruptible {
                    dav.options { davCapabilities, _ ->
                        if (CollectionUtils.containsAny(davCapabilities, "1", "2", "3"))
                            supported = true
                    }
                }
            }

        supported
    }



}