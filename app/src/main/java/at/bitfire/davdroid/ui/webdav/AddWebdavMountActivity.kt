package at.bitfire.davdroid.ui.webdav

import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.databinding.ActivityAddWebdavMountBinding
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.model.Credentials
import at.bitfire.davdroid.model.WebDavMount
import at.bitfire.davdroid.webdav.CredentialsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.logging.Level

class AddWebdavMountActivity: AppCompatActivity() {

    lateinit var binding: ActivityAddWebdavMountBinding
    val model by viewModels<Model>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddWebdavMountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.model = model

        binding.addMount.setOnClickListener {
            validate()
        }
    }

    private fun validate() {
        val url = model.url.value?.toHttpUrlOrNull() ?: return

        val mount = WebDavMount(
            name = model.displayName.value ?: return,
            url = UrlUtils.withTrailingSlash(url)
        )

        val userName = model.userName.value
        val password = model.password.value
        val credentials =
            if (userName != null && password != null)
                Credentials(userName, password)
            else
                null

        binding.progress.visibility = View.VISIBLE
        binding.addMount.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            if (model.addMount(mount, credentials))
                finish()

            launch(Dispatchers.Main) {
                binding.progress.visibility = View.GONE
                binding.addMount.isEnabled = true
            }
        }
    }


    class Model(app: Application): AndroidViewModel(app) {

        val displayName = MutableLiveData<String>()
        val url = MutableLiveData<String>()
        val userName = MutableLiveData<String>()
        val password = MutableLiveData<String>()

        @WorkerThread
        fun addMount(mount: WebDavMount, credentials: Credentials?): Boolean {
            val supportsDav = try {
                hasWebDav(mount, credentials)
            } catch (e: Exception) {
                Logger.log.log(Level.WARNING, "Couldn't query WebDAV support", e)
                false
            }
            if (!supportsDav)
                return false

            val db = AppDatabase.getInstance(getApplication())
            val id = db.webDavMountDao().insert(mount)
            if (credentials != null) {
                val credentialsStore = CredentialsStore(getApplication())
                credentialsStore.setCredentials(id, credentials)
            }
            return true
        }

        private fun hasWebDav(mount: WebDavMount, credentials: Credentials?): Boolean {
            var supported = false
            HttpClient.Builder(getApplication(), null, credentials).build().use { client ->
                val dav = DavResource(client.okHttpClient, mount.url)
                dav.options { davCapabilities, _ ->
                    if (davCapabilities.contains("1"))
                        supported = true
                }
            }
            return supported
        }

    }

}