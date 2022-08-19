/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.DebugInfoActivity
import com.google.android.material.snackbar.Snackbar
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject


class NextcloudLoginFlowFragment: Fragment() {

    companion object {

        const val LOGIN_FLOW_V1_PATH = "/index.php/login/flow"
        const val LOGIN_FLOW_V2_PATH = "/index.php/login/v2"

        /** Set this to 1 to indicate that Login Flow shall be used. */
        const val EXTRA_LOGIN_FLOW = "loginFlow"

        /** Path to DAV endpoint (e.g. `/remote.php/dav`). Will be appended to the
         *  server URL returned by Login Flow without further processing. */
        const val EXTRA_DAV_PATH = "davPath"

        const val REQUEST_BROWSER = 0
    }

    val loginModel by activityViewModels<LoginModel>()
    val loginFlowModel by viewModels<LoginFlowModel>()


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = View(requireActivity())

        val entryUrl = requireActivity().intent.data ?: throw IllegalArgumentException("Intent data must be set to Login Flow URL")
        Logger.log.info("Using Login Flow entry point: $entryUrl")

        loginFlowModel.loginUrl.observe(viewLifecycleOwner) { loginUrl ->
            if (loginUrl == null)
                return@observe
            val loginUri = loginUrl.toUri()

            // reset URL so that the browser isn't shown another time
            loginFlowModel.loginUrl.value = null

            if (haveCustomTabs(loginUri)) {
                // Custom Tabs are available
                val browser = CustomTabsIntent.Builder()
                        .setToolbarColor(resources.getColor(R.color.primaryColor))
                        .build()
                browser.intent.data = loginUri
                startActivityForResult(browser.intent, REQUEST_BROWSER, browser.startAnimationBundle)

            } else {
                // fallback: launch normal browser
                val browser = Intent(Intent.ACTION_VIEW, loginUri)
                browser.addCategory(Intent.CATEGORY_BROWSABLE)
                if (browser.resolveActivity(requireActivity().packageManager) != null)
                    startActivityForResult(browser, REQUEST_BROWSER)
                else
                    Snackbar.make(view, getString(R.string.install_browser), Snackbar.LENGTH_INDEFINITE).show()
            }
        }

        loginFlowModel.error.observe(viewLifecycleOwner) { exception ->
            Snackbar.make(requireView(), exception.toString(), Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.exception_show_details) {
                        val intent = DebugInfoActivity.IntentBuilder(requireActivity())
                            .withCause(exception)
                            .build()
                        startActivity(intent)
                    }
                    .show()
        }

        loginFlowModel.loginData.observe(viewLifecycleOwner) { (baseUri, credentials) ->
            // continue to next fragment
            loginModel.baseURI = baseUri
            loginModel.credentials = credentials
            parentFragmentManager.beginTransaction()
                    .replace(android.R.id.content, DetectConfigurationFragment(), null)
                    .addToBackStack(null)
                    .commit()
        }

        // start Login Flow
        loginFlowModel.setUrl(entryUrl)

        return view
    }

    private fun haveCustomTabs(uri: Uri): Boolean {
        val browserIntent = Intent()
                .setAction(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(uri)
        val pm = requireActivity().packageManager
        val appsSupportingCustomTabs = pm.queryIntentActivities(browserIntent, 0)
        for (pkg in appsSupportingCustomTabs) {
            // check whether app resolves Custom Tabs service, too
            val serviceIntent = Intent(ACTION_CUSTOM_TABS_CONNECTION).apply {
                setPackage(pkg.activityInfo.packageName)
            }
            if (pm.resolveService(serviceIntent, 0) != null)
                return true
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_BROWSER)
            return

        val davPath = requireActivity().intent.getStringExtra(EXTRA_DAV_PATH)
        loginFlowModel.checkResult(davPath)
    }


    /**
     * Implements Login Flow v2.
     *
     * @see https://docs.nextcloud.com/server/20/developer_manual/client_apis/LoginFlow/index.html#login-flow-v2
     */
    class LoginFlowModel(app: Application): AndroidViewModel(app) {

        val error = MutableLiveData<Exception>()
        val loginUrl = MutableLiveData<String>()

        val httpClient by lazy {
            HttpClient.Builder(getApplication())
                    .setForeground(true)
                    .build()
        }

        var pollUrl: HttpUrl? = null
        var token: String? = null

        val loginData = MutableLiveData<Pair<URI, Credentials>>()

        override fun onCleared() {
            httpClient.close()
        }


        @UiThread
        fun setUrl(entryUri: Uri) {
            val entryUrl = entryUri.toString()
            val v2Url =
                    if (entryUrl.endsWith(LOGIN_FLOW_V1_PATH))
                        // got Login Flow v1 URL, rewrite to v2
                        entryUrl.removeSuffix(LOGIN_FLOW_V1_PATH) + LOGIN_FLOW_V2_PATH
                    else
                        entryUrl

            // send POST request and process JSON reply
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val json = postForJson(v2Url.toHttpUrl(), "".toRequestBody())

                    // login URL
                    loginUrl.postValue(json.getString("login"))

                    // poll URL and token
                    json.getJSONObject("poll").let { poll ->
                        pollUrl = poll.getString("endpoint").toHttpUrl()
                        token = poll.getString("token")
                    }
                } catch (e: Exception) {
                    error.postValue(e)
                }
            }
        }

        @UiThread
        fun checkResult(davPath: String?) {
            val pollUrl = pollUrl ?: return
            val token = token ?: return

            CoroutineScope(Dispatchers.IO).launch {
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
                    error.postValue(e)
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
                if (intent.hasExtra(EXTRA_LOGIN_FLOW))
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