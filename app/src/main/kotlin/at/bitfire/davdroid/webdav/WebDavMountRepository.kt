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

    suspend fun addMount(
        url: HttpUrl,
        displayName: String,
        credentials: Credentials?
    ) {
        withContext(Dispatchers.IO) {
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
        }
    }

    suspend fun hasWebDav(
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