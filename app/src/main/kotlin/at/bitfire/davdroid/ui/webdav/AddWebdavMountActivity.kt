/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.webdav

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.WebDavMount
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.composable.PasswordTextField
import at.bitfire.davdroid.webdav.CredentialsStore
import at.bitfire.davdroid.webdav.DavDocumentsProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
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
class AddWebdavMountActivity : AppCompatActivity() {

    val model by viewModels<Model>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val isLoading by model.isLoading.observeAsState(initial = false)
            val error by model.error.observeAsState(initial = null)
            val displayName by model.displayName.observeAsState(initial = "")
            val displayNameError by model.displayNameError.observeAsState(initial = null)
            val url by model.url.observeAsState(initial = "")
            val urlError by model.urlError.observeAsState(initial = null)
            val username by model.userName.observeAsState(initial = "")
            val password by model.password.observeAsState(initial = "")

            AppTheme {
                Layout(
                    isLoading = isLoading,
                    error = error,
                    onErrorClearRequested = { model.error.value = null },
                    displayName = displayName,
                    onDisplayNameChange = model.displayName::setValue,
                    displayNameError = displayNameError,
                    url = url,
                    onUrlChange = model.url::setValue,
                    urlError = urlError,
                    username = username,
                    onUsernameChange = model.userName::setValue,
                    password = password,
                    onPasswordChange = model.password::setValue
                )
            }
        }
    }


    @Composable
    fun Layout(
        isLoading: Boolean = false,
        error: String? = null,
        onErrorClearRequested: () -> Unit = {},
        displayName: String = "",
        onDisplayNameChange: (String) -> Unit = {},
        displayNameError: String? = null,
        url: String = "",
        onUrlChange: (String) -> Unit = {},
        urlError: String? = null,
        username: String = "",
        onUsernameChange: (String) -> Unit = {},
        password: String = "",
        onPasswordChange: (String) -> Unit = {}
    ) {
        val uriHandler = LocalUriHandler.current

        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(error) {
            if (error != null) {
                snackbarHostState.showSnackbar(
                    message = error
                )
                onErrorClearRequested()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { onSupportNavigateUp() }) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                        }
                    },
                    title = { Text(stringResource(R.string.webdav_add_mount_title)) },
                    actions = {
                        IconButton(
                            onClick = {
                                uriHandler.openUri(
                                    Constants.HOMEPAGE_URL.buildUpon()
                                        .appendPath(Constants.HOMEPAGE_PATH_TESTED_SERVICES)
                                        .withStatParams("AddWebdavMountActivity")
                                        .build().toString()
                                )
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Help, stringResource(R.string.help))
                        }
                    }
                )
            },
            bottomBar = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            enabled = !isLoading,
                            onClick = ::validate
                        ) {
                            Text(
                                text = stringResource(R.string.webdav_add_mount_add).uppercase()
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                if (isLoading)
                    LinearProgressIndicator(
                        color = MaterialTheme.colors.secondary,
                        modifier = Modifier.fillMaxWidth()
                    )

                Form(
                    displayName,
                    onDisplayNameChange,
                    displayNameError,
                    url,
                    onUrlChange,
                    urlError,
                    username,
                    onUsernameChange,
                    password,
                    onPasswordChange
                )
            }
        }
    }

    @Composable
    fun Form(
        displayName: String,
        onDisplayNameChange: (String) -> Unit,
        displayNameError: String?,
        url: String,
        onUrlChange: (String) -> Unit,
        urlError: String?,
        username: String,
        onUsernameChange: (String) -> Unit,
        password: String,
        onPasswordChange: (String) -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            FormField(
                displayName,
                onDisplayNameChange,
                displayNameError,
                R.string.webdav_add_mount_display_name
            )
            FormField(
                url,
                onUrlChange,
                urlError,
                R.string.webdav_add_mount_url
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.webdav_add_mount_authentication),
                style = MaterialTheme.typography.body1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            FormField(
                username,
                onUsernameChange,
                null,
                R.string.webdav_add_mount_username
            )

            PasswordTextField(
                password = password,
                onPasswordChange = onPasswordChange,
                labelText = stringResource(R.string.webdav_add_mount_password)
            )
        }
    }

    @Composable
    fun FormField(
        value: String,
        onValueChange: (String) -> Unit,
        error: String?,
        @StringRes label: Int
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            label = { Text(stringResource(label)) },
            singleLine = true,
            isError = error != null
        )
        if (error != null) {
            Text(
                text = error,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.caption.copy(
                    color = MaterialTheme.colors.error
                )
            )
        }
    }

    @Preview
    @Composable
    fun Layout_Preview() {
        AppTheme {
            Layout()
        }
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
    }


    @HiltViewModel
    class Model @Inject constructor(
        val context: Application,
        val db: AppDatabase
    ): ViewModel() {

        val displayName = MutableLiveData<String>()
        val displayNameError = MutableLiveData<String>()
        val url = MutableLiveData<String>()
        val urlError = MutableLiveData<String>()
        val userName = MutableLiveData<String>()
        val password = MutableLiveData<String>()

        val error = MutableLiveData<String>()
        val isLoading = MutableLiveData(false)


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

    }

}