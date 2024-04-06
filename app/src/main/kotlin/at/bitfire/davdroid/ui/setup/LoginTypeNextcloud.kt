/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.ui.UiUtils.haveCustomTabs
import at.bitfire.davdroid.ui.composable.Assistant
import at.bitfire.davdroid.ui.setup.LoginTypeNextcloud.LOGIN_FLOW_V1_PATH
import at.bitfire.davdroid.ui.setup.LoginTypeNextcloud.LOGIN_FLOW_V2_PATH
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
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

object LoginTypeNextcloud : LoginType {

    override val title: Int
        get() = R.string.login_type_nextcloud

    override val helpUrl: Uri
        get() = Constants.HOMEPAGE_URL.buildUpon()
            .appendPath(Constants.HOMEPAGE_PATH_TESTED_SERVICES)
            .appendPath("nextcloud")
            .withStatParams("LoginTypeNextcloud")
            .build()

    const val LOGIN_FLOW_V1_PATH = "index.php/login/flow"
    const val LOGIN_FLOW_V2_PATH = "index.php/login/v2"

    /** Path to DAV endpoint (e.g. `/remote.php/dav`). Will be appended to the
     *  server URL returned by Login Flow without further processing. */
    const val DAV_PATH = "/remote.php/dav"


    @Composable
    override fun Content(
        snackbarHostState: SnackbarHostState,
        loginInfo: LoginInfo,
        onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit,
        onDetectResources: () -> Unit,
        onFinish: () -> Unit
    ) {
        val context = LocalContext.current
        val locale = Locale.current
        val scope = rememberCoroutineScope()
        val model = viewModel<Model>()

        val checkResultCallback = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            model.checkResult()
        }

        val loginUrl = model.loginUrl
        LaunchedEffect(loginUrl) {
            loginUrl?.toUri()?.let { loginUri ->
                if (haveCustomTabs(context)) {
                    // Custom Tabs are available
                    @Suppress("DEPRECATION")
                    val browser = CustomTabsIntent.Builder()
                        .setToolbarColor(context.resources.getColor(R.color.primaryColor))
                        .build()
                    browser.intent.data = loginUri
                    browser.intent.putExtra(
                        Browser.EXTRA_HEADERS,
                        bundleOf("Accept-Language" to locale.toLanguageTag())
                    )
                    checkResultCallback.launch(browser.intent)
                } else {
                    // fallback: launch normal browser
                    val browser = Intent(Intent.ACTION_VIEW, loginUri)
                    browser.addCategory(Intent.CATEGORY_BROWSABLE)
                    if (browser.resolveActivity(context.packageManager) != null)
                        checkResultCallback.launch(browser)
                    else
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.install_browser))
                        }
                }
            }
        }

        val resultLoginInfo = model.loginInfo
        LaunchedEffect(resultLoginInfo) {
            resultLoginInfo?.let {
                onUpdateLoginInfo(it)
                onDetectResources()
            }
        }

        NextcloudLoginScreen(
            loginInfo = loginInfo,
            onUpdateLoginInfo = onUpdateLoginInfo,
            inProgress = model.inProgress,
            error = model.error,
            onLaunchLoginFlow = { entryUrl ->
                model.start(entryUrl)
            }
        )
    }


    /**
     * Implements Login Flow v2.
     *
     * @see https://docs.nextcloud.com/server/20/developer_manual/client_apis/LoginFlow/index.html#login-flow-v2
     */
    @HiltViewModel
    class Model @Inject constructor(
        val context: Application,
        val state: SavedStateHandle
    ): ViewModel() {

        companion object {
            const val STATE_POLL_URL = "poll_url"
            const val STATE_TOKEN = "token"
        }

        private val httpClient = HttpClient.Builder(context)
            .setForeground(true)
            .build()
        var inProgress by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)

        var loginUrl by mutableStateOf<String?>(null)
        private var pollUrl: HttpUrl?
            get() = state.get<String>(STATE_POLL_URL)?.toHttpUrlOrNull()
            set(value) {
                state[STATE_POLL_URL] = value.toString()
            }
        private var token: String?
            get() = state.get<String>(STATE_TOKEN)
            set(value) {
                state[STATE_TOKEN] = value
            }

        var loginInfo by mutableStateOf<LoginInfo?>(null)


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
        fun start(entryUrl: HttpUrl) = viewModelScope.launch {
            inProgress = true

            error = null
            pollUrl = null
            token = null

            var entryUrlStr = entryUrl.toString()
            if (entryUrlStr.endsWith(LOGIN_FLOW_V1_PATH))
                // got Login Flow v1 URL, rewrite to v2
                entryUrlStr = entryUrlStr.removeSuffix(LOGIN_FLOW_V1_PATH)

            val v2Url = entryUrlStr.toHttpUrl().newBuilder()
                .addPathSegments(LOGIN_FLOW_V2_PATH)
                .build()

            // send POST request and process JSON reply
            try {
                val json = withContext(Dispatchers.IO) {
                    postForJson(v2Url, "".toRequestBody())
                }

                // login URL
                loginUrl = json.getString("login")

                // poll URL and token
                json.getJSONObject("poll").let { poll ->
                    pollUrl = poll.getString("endpoint").toHttpUrl()
                    token = poll.getString("token")
                }
            } catch (e: Exception) {
                Logger.log.log(Level.WARNING, "Couldn't obtain login URL", e)
                error = context.getString(R.string.login_nextcloud_login_flow_no_login_url)
            } finally {
                inProgress = false
            }
        }

        /**
         * Called when the custom tab / browser activity is finished. If memory is low, our
         * [LoginTypeNextcloud] and its model have been cleared in the meanwhile. So if
         * we need certain data from the model, we have to make sure that these data are retained when the
         * model is cleared (saved state).
         */
        @UiThread
        fun checkResult() = viewModelScope.launch {
            val pollUrl = pollUrl ?: return@launch
            val token = token ?: return@launch

            try {
                val json = withContext(Dispatchers.IO) {
                    postForJson(pollUrl, "token=$token".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                }
                val serverUrl = json.getString("server")
                val loginName = json.getString("loginName")
                val appPassword = json.getString("appPassword")

                val baseUri = URI.create(serverUrl + DAV_PATH)

                loginInfo = LoginInfo(
                    baseUri = baseUri,
                    credentials = Credentials(loginName, appPassword),
                    suggestedGroupMethod = GroupMethod.CATEGORIES
                )
            } catch (e: Exception) {
                Logger.log.log(Level.WARNING, "Polling login URL failed", e)
                error = context.getString(R.string.login_nextcloud_login_flow_no_login_data)
            }
        }

        @WorkerThread
        private suspend fun postForJson(url: HttpUrl, requestBody: RequestBody): JSONObject {
            val postRq = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            val response = runInterruptible {
                httpClient.okHttpClient.newCall(postRq).execute()
            }

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

}


@Composable
fun NextcloudLoginScreen(
    loginInfo: LoginInfo,
    onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit,
    inProgress: Boolean,
    error: String? = null,
    onLaunchLoginFlow: (entryUrl: HttpUrl) -> Unit
) {
    var entryUrl by remember { mutableStateOf(loginInfo.baseUri?.toString() ?: "") }

    val newLoginInfo = LoginInfo(
        baseUri = try {
            URI(
                if (entryUrl.startsWith("http://", ignoreCase = true) ||
                    entryUrl.startsWith("https://", ignoreCase = true))
                    entryUrl
                else
                    "https://$entryUrl"
            )
        } catch (_: Exception) {
            null
        }
    )
    onUpdateLoginInfo(newLoginInfo)

    val onLogin = {
        if (newLoginInfo.baseUri != null && !inProgress)
            onLaunchLoginFlow(newLoginInfo.baseUri.toHttpUrlOrNull()!!)
    }

    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = newLoginInfo.baseUri != null,
        onNext = onLogin
    ) {
        if (inProgress)
            LinearProgressIndicator(
                color = MaterialTheme.colors.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_nextcloud_login_with_nextcloud),
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Column {
                Text(
                    stringResource(R.string.login_nextcloud_login_flow_text),
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(top = 8.dp)
                )

                val focusRequester = remember { FocusRequester() }
                OutlinedTextField(
                    value = entryUrl,
                    onValueChange = { entryUrl = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .focusRequester(focusRequester),
                    enabled = !inProgress,
                    leadingIcon = {
                        Icon(Icons.Default.Cloud, null)
                    },
                    label = {
                        Text(stringResource(R.string.login_nextcloud_login_flow_server_address))
                    },
                    placeholder = { Text("cloud.example.com") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onLogin() }
                    ),
                    singleLine = true
                )
                LaunchedEffect(Unit) {
                    if (loginInfo.baseUri == null)
                        focusRequester.requestFocus()
                }

                if (error != null)
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                error,
                                style = MaterialTheme.typography.body1
                            )
                        }
                    }
            }
        }
    }
}

@Composable
@Preview
fun NextcloudLoginScreen_Preview() {
    NextcloudLoginScreen(
        loginInfo = LoginInfo(),
        onUpdateLoginInfo = {},
        inProgress = false,
        onLaunchLoginFlow = {}
    )
}

@Composable
@Preview
fun NextcloudLoginScreen_Preview_InProgressError() {
    NextcloudLoginScreen(
        loginInfo = LoginInfo(),
        onUpdateLoginInfo = {},
        inProgress = true,
        error = "Some Error",
        onLaunchLoginFlow = {}
    )
}