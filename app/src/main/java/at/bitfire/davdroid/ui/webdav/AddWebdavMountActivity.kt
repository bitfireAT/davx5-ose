/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.webdav

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.davdroid.App
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityAddWebdavMountBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.UiUtils
import at.bitfire.davdroid.webdav.CredentialsStore
import at.bitfire.davdroid.webdav.DavDocumentsProvider
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.apache.commons.collections4.CollectionUtils
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level
import javax.inject.Inject

@AndroidEntryPoint
class AddWebdavMountActivity: AppCompatActivity() {

    lateinit var binding: ActivityAddWebdavMountBinding
    val model by viewModels<Model>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddWebdavMountBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        binding.model = model
        setContentView(binding.root)

        model.error.observe(this) { error ->
            if (error != null) {
                Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                model.error.value = null
            }
        }

        binding.addMount.setOnClickListener {
            validate()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_add_webdav_mount, menu)
        return true
    }

    fun onShowHelp(item: MenuItem) {
        UiUtils.launchUri(this,
            App.homepageUrl(this).buildUpon().appendPath("tested-with").build())
    }


    private fun validate() {
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
            binding.progress.visibility = View.VISIBLE
            binding.addMount.isEnabled = false

            val mount = WebDavMount(
                name = model.displayName.value ?: return,
                url = UrlUtils.withTrailingSlash(url)
            )
            lifecycleScope.launch(Dispatchers.IO) {
                if (model.addMount(mount, credentials))
                    finish()

                launch(Dispatchers.Main) {
                    binding.progress.visibility = View.INVISIBLE
                    binding.addMount.isEnabled = true
                }
            }
        }
    }


    @HiltViewModel
    class Model @Inject constructor(
        @ApplicationContext val context: Context,
        val db: AppDatabase
    ) : ViewModel() {

        val displayName = MutableLiveData<String>()
        val displayNameError = MutableLiveData<String>()
        val url = MutableLiveData<String>()
        val urlError = MutableLiveData<String>()
        val userName = MutableLiveData<String>()
        val password = MutableLiveData<String>()

        val error = MutableLiveData<String>()


        @WorkerThread
        fun addMount(mount: WebDavMount, credentials: Credentials?): Boolean {
            val supportsDav = try {
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
            DavDocumentsProvider.notifyMountsChanged(context)

            return true
        }

        fun hasWebDav(mount: WebDavMount, credentials: Credentials?): Boolean {
            var supported = false
            HttpClient.Builder(context, null, credentials).build().use { client ->
                val dav = DavResource(client.okHttpClient, mount.url)
                dav.options { davCapabilities, _ ->
                    if (CollectionUtils.containsAny(davCapabilities, "1", "2", "3"))
                        supported = true
                }
            }
            return supported
        }

    }

}