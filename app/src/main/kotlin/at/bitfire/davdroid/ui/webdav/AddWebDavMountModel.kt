/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.webdav

import android.app.Application
import androidx.annotation.WorkerThread
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import at.bitfire.dav4jvm.DavResource
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.network.HttpClient
import dagger.hilt.android.lifecycle.HiltViewModel
import org.apache.commons.collections4.CollectionUtils
import javax.inject.Inject

@HiltViewModel
class AddWebDavMountModel @Inject constructor(
    val context: Application,
    val db: AppDatabase
): ViewModel() {

    data class UiState(
        val displayName: String = "",
        val url: String = "",
        val username: String = "",
        val password: String = "",
        val certAlias: String = "",
        val isLoading: Boolean = false,
        val error: String? = null
    )

    var uiState = mutableStateOf(UiState())
        private set

    @WorkerThread
    fun addMount(mount: WebDavMount, credentials: Credentials?): Boolean {
        /*val supportsDav = try {
            hasWebDav(mount, credentials)
        } catch (e: Exception) {
            Logger.log.log(Level.WARNING, "Couldn't query WebDAV support", e)
            error.postValue(e.localizedMessage)
            return false
        }
        if (!supportsDav) {
            error.postValue(context.getString(R.string.webdav_add_mount_no_support))
            return false
        }

        val id = db.webDavMountDao().insert(mount)

        val credentialsStore = CredentialsStore(context)
        credentialsStore.setCredentials(id, credentials)

        // notify content URI listeners
        DavDocumentsProvider.notifyMountsChanged(context)*/

        return true
    }

    fun hasWebDav(mount: WebDavMount, credentials: Credentials?): Boolean {
        // TODO move to repository

        var supported = false
        HttpClient.Builder(context, null, credentials)
            .setForeground(true)
            .build()
            .use { client ->
                val dav = DavResource(client.okHttpClient, mount.url)
                dav.options { davCapabilities, _ ->
                    if (CollectionUtils.containsAny(davCapabilities, "1", "2", "3"))
                        supported = true
                }
            }
        return supported
    }


    /*private fun validate() {
    var ok = true

    val displayName = model.displayName.value
    model.displayNameError.value = null
    if (displayName.isNullOrBlank()) {
        ok = false
        model.displayNameError.value = getString(R.string.field_required)
    }

    var url: HttpUrl? = null
    model.urlError.value = null
    val rawUrl = model.url.value
    if (rawUrl.isNullOrBlank()) {
        ok = false
        model.urlError.value = getString(R.string.field_required)
    } else {
        try {
            var uri = URI(rawUrl)
            if (uri.scheme == null)
                uri = URI("https", uri.schemeSpecificPart, null)
            url = uri.toHttpUrlOrNull()
            if (url == null) {
                // should never happen
                ok = false
                model.urlError.value = getString(R.string.webdav_add_mount_url_invalid)
            }
        } catch (e: URISyntaxException) {
            ok = false
            model.urlError.value = e.localizedMessage
        }
    }

    val userName = model.userName.value
    val password = model.password.value
    val credentials =
        if (userName != null && password != null)
            Credentials(userName, password)
        else
            null

    if (ok && url != null) {
        model.isLoading.postValue(true)

        val mount = WebDavMount(
            name = model.displayName.value ?: return,
            url = UrlUtils.withTrailingSlash(url)
        )
        lifecycleScope.launch(Dispatchers.IO) {
            if (model.addMount(mount, credentials))
                finish()

            model.isLoading.postValue(false)
        }
    }
}*/

}