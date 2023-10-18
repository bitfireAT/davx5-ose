/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.Browser
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.ui.UiUtils.haveCustomTabs
import com.google.accompanist.themeadapter.material.MdcTheme
import com.google.android.material.snackbar.Snackbar
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.logging.Level
import javax.inject.Inject

class NextcloudLoginFlowFragment: Fragment() {

    companion object {

        const val LOGIN_FLOW_V1_PATH = "index.php/login/flow"
        val LOGIN_FLOW_V2_PATH = "index.php/login/v2"

        /** Set this to 1 to indicate that Login Flow shall be used. */
        const val EXTRA_LOGIN_FLOW = "loginFlow"

        /** Path to DAV endpoint (e.g. `/remote.php/dav`). Will be appended to the
         *  server URL returned by Login Flow without further processing. */
        const val EXTRA_DAV_PATH = "davPath"
    }

    val loginModel by activityViewModels<LoginModel>()
    val model by viewModels<Model>()

    val checkResultCallback = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val davPath = requireActivity().intent.getStringExtra(EXTRA_DAV_PATH)
        model.checkResult(davPath)
    }


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val entryUrl = requireActivity().intent.data?.toString()?.toHttpUrlOrNull()

        val view = ComposeView(requireActivity()).apply {
            setContent {
                MdcTheme {
                    NextcloudLoginComposable(
                        onStart = { url ->
                            model.start(url)
                        },
                        entryUrl = entryUrl,
                        inProgress = model.inProgress.observeAsState(false),
                        error = model.error.observeAsState()
                    )
                }
            }
        }

        model.loginUrl.observe(viewLifecycleOwner) { loginUrl ->
            if (loginUrl == null)
                return@observe
            val loginUri = loginUrl.toUri()

            // reset URL so that the browser isn't shown another time
            model.loginUrl.value = null

            if (haveCustomTabs(requireActivity())) {
                // Custom Tabs are available
                @Suppress("DEPRECATION")
                val browser = CustomTabsIntent.Builder()
                    .setToolbarColor(resources.getColor(R.color.primaryColor))
                    .build()
                browser.intent.data = loginUri
                browser.intent.putExtra(
                    Browser.EXTRA_HEADERS,
                    bundleOf("Accept-Language" to Locale.current.toLanguageTag())
                )
                checkResultCallback.launch(browser.intent)
            } else {
                // fallback: launch normal browser
                val browser = Intent(Intent.ACTION_VIEW, loginUri)
                browser.addCategory(Intent.CATEGORY_BROWSABLE)
                if (browser.resolveActivity(requireActivity().packageManager) != null)
                    checkResultCallback.launch(browser)
                else
                    Snackbar.make(view, getString(R.string.install_browser), Snackbar.LENGTH_INDEFINITE).show()
            }
        }

        model.loginData.observe(viewLifecycleOwner) { loginData ->
            if (loginData == null)
                return@observe
            val (baseUri, credentials) = loginData

            // continue to next fragment
            loginModel.baseURI = baseUri
            loginModel.credentials = credentials
            parentFragmentManager.beginTransaction()
                    .replace(android.R.id.content, DetectConfigurationFragment(), null)
                    .addToBackStack(null)
                    .commit()

            // reset loginData so that we can go back
            model.loginData.value = null
        }

        if (savedInstanceState == null && entryUrl != null)
            model.start(entryUrl)

        return view
    }


    /**
     * Implements Login Flow v2.
     *
     * @see https://docs.nextcloud.com/server/20/developer_manual/client_apis/LoginFlow/index.html#login-flow-v2
     */
    class Model(app: Application): AndroidViewModel(app) {
        val loginUrl = MutableLiveData<String>()
        val error = MutableLiveData<String>()

        val httpClient by lazy {
            HttpClient.Builder(getApplication())
                .setForeground(true)
                .build()
        }
        val inProgress = MutableLiveData<Boolean>(false)

        var pollUrl: HttpUrl? = null
        var token: String? = null

        val loginData = MutableLiveData<Pair<URI, Credentials>>()

        override fun onCleared() {
            httpClient.close()
        }


        /**
         * Starts the Login Flow.
         *
         * @param entryUrl entryURL: either a Login Flow path (ending with [LOGIN_FLOW_V1_PATH] or [LOGIN_FLOW_V2_PATH]),
         * or another URL which is treated as Nextcloud root URL. In this case, [LOGIN_FLOW_V2_PATH] is appended.
         */
        @UiThread
        fun start(entryUrl: HttpUrl) {
            inProgress.value = true
            error.value = null

            var entryUrlStr = entryUrl.toString()
            if (entryUrlStr.endsWith(LOGIN_FLOW_V1_PATH))
                // got Login Flow v1 URL, rewrite to v2
                entryUrlStr = entryUrlStr.removeSuffix(LOGIN_FLOW_V1_PATH)

            val v2Url = entryUrlStr.toHttpUrl().newBuilder()
                .addPathSegments(LOGIN_FLOW_V2_PATH)
                .build()

            // send POST request and process JSON reply
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val json = postForJson(v2Url, "".toRequestBody())

                    // login URL
                    loginUrl.postValue(json.getString("login"))

                    // poll URL and token
                    json.getJSONObject("poll").let { poll ->
                        pollUrl = poll.getString("endpoint").toHttpUrl()
                        token = poll.getString("token")
                    }
                } catch (e: Exception) {
                    Logger.log.log(Level.WARNING, "Couldn't obtain login URL", e)
                    error.postValue(getApplication<Application>().getString(R.string.login_nextcloud_login_flow_no_login_url))
                } finally {
                    inProgress.postValue(false)
                }
            }
        }

        @UiThread
        fun checkResult(davPath: String?) {
            val pollUrl = pollUrl ?: return
            val token = token ?: return

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val json = postForJson(pollUrl, "token=$token".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    val serverUrl = json.getString("server")
                    val loginName = json.getString("loginName")
                    val appPassword = json.getString("appPassword")

                    val baseUri = if (davPath != null)
                        URI.create(serverUrl + davPath)
                    else
                        URI.create(serverUrl)

                    loginData.postValue(Pair(
                        baseUri,
                        Credentials(loginName, appPassword)
                    ))
                } catch (e: Exception) {
                    Logger.log.log(Level.WARNING, "Polling login URL failed", e)
                    error.postValue(getApplication<Application>().getString(R.string.login_nextcloud_login_flow_no_login_data))
                }
            }
        }

        @WorkerThread
        private fun postForJson(url: HttpUrl, requestBody: RequestBody): JSONObject {
            val postRq = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
            val response = httpClient.okHttpClient.newCall(postRq).execute()

            if (response.code != HttpURLConnection.HTTP_OK)
                throw HttpException(response)

            response.body?.use { body ->
                val mimeType = body.contentType() ?: throw DavException("Login Flow response without MIME type")
                if (mimeType.type != "application" || mimeType.subtype != "json")
                    throw DavException("Invalid Login Flow response (not JSON)")

                // decode JSON
                return JSONObject(body.string())
            }

            throw DavException("Invalid Login Flow response (no body)")
        }

    }


    class Factory @Inject constructor(): LoginCredentialsFragmentFactory {

        override fun getFragment(intent: Intent) =
            if (intent.hasExtra(EXTRA_LOGIN_FLOW) && intent.data != null)
                NextcloudLoginFlowFragment()
            else
                null

    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class NextcloudLoginFlowFragmentModule {
        @Binds
        @IntoMap
        @IntKey(/* priority */ 20)
        abstract fun factory(impl: Factory): LoginCredentialsFragmentFactory
    }

}


@Composable
fun NextcloudLoginComposable(
    entryUrl: HttpUrl?,
    inProgress: State<Boolean>,
    error: State<String?>,
    onStart: (HttpUrl) -> Unit
) {
    Column {
        if (inProgress.value)
            LinearProgressIndicator(
                color = MaterialTheme.colors.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_nextcloud_login_with_nextcloud),
                style = MaterialTheme.typography.h5
            )
            NextcloudLoginFlowComposable(
                providedEntryUrl = entryUrl,
                inProgress = inProgress,
                error = error,
                onStart = onStart
            )
        }
    }
}


@Composable
fun NextcloudLoginFlowComposable(
    providedEntryUrl: HttpUrl?,
    inProgress: State<Boolean>,
    error: State<String?>,
    onStart: ((HttpUrl) -> Unit)
) {
    Column {
        Text(
            stringResource(R.string.login_nextcloud_login_flow),
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            stringResource(R.string.login_nextcloud_login_flow_text),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        val entryUrlStr = remember { mutableStateOf(providedEntryUrl?.toString() ?: "") }
        val entryUrl = remember { mutableStateOf<HttpUrl?>(providedEntryUrl) }
        OutlinedTextField(entryUrlStr.value,
            onValueChange = { newUrlStr ->
                entryUrlStr.value = newUrlStr

                entryUrl.value = try {
                    val withScheme =
                        if (!newUrlStr.startsWith("http://", true) && !newUrlStr.startsWith("https://", true))
                            "https://$newUrlStr"
                        else
                            newUrlStr
                    withScheme.toHttpUrl()
                } catch (e: IllegalArgumentException) {
                    null
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            readOnly = inProgress.value,
            label = {
                Text(stringResource(R.string.login_nextcloud_login_flow_server_address))
            },
            placeholder = {
                Text("cloud.example.com")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    entryUrl.value?.let(onStart)
                }
            ),
            singleLine = true
        )

        Button(
            onClick = {
                entryUrl.value?.let(onStart)
            },
            enabled = entryUrl.value != null && !inProgress.value
        ) {
            Text(stringResource(R.string.login_nextcloud_login_flow_sign_in))
        }

        error.value?.let { msg ->
            Text(
                msg,
                color = MaterialTheme.colors.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
@Preview
fun NextcloudLoginFlowComposable_PreviewWithError() {
    NextcloudLoginFlowComposable(
        providedEntryUrl = null,
        inProgress = remember { mutableStateOf(true) },
        error = remember { mutableStateOf("Something wrong happened") },
        onStart = { }
    )
}