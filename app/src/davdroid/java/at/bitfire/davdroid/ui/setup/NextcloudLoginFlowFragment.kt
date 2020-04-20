package at.bitfire.davdroid.ui.setup

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.net.http.SslError
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.Credentials
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_webview.view.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URI
import java.util.logging.Level

class NextcloudLoginFlowFragment: Fragment() {

    companion object {

        /** Set this to 1 to indicate that Login Flow shall be used. */
        const val EXTRA_LOGIN_FLOW = "loginFlow"

        /** Path to DAV endpoint (e.g. `/remote.php/dav`). Will be appended to the
         *  server URL returned by Login Flow without further processing. */
        const val EXTRA_DAV_PATH = "davPath"
    }

    lateinit var loginModel: LoginModel


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        loginModel = ViewModelProvider(requireActivity()).get(LoginModel::class.java)

        val view = inflater.inflate(R.layout.activity_webview, container, false)
        val progressBar = view.progress

        val loginUrl = requireActivity().intent.data ?: throw IllegalArgumentException("Intent data must be set to Login Flow URL")
        Logger.log.info("Using Login Flow URL: $loginUrl")

        val webView = view.browser
        webView.settings.apply {
            javaScriptEnabled = true
            userAgentString = BuildConfig.userAgent
        }
        webView.webViewClient = CustomWebViewClient()
        webView.webChromeClient = object: WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress == 100) View.INVISIBLE else View.VISIBLE
            }
        }
        webView.loadUrl(
                loginUrl.toString(),   // https://nextcloud.example.com/index.php/login/flow
                mapOf(Pair("OCS-APIREQUEST", "true"))
        )
        return view
    }

    private fun onReceivedNcUrl(url: String) {
        val format = Regex("^nc://login/server:(.+)&user:(.+)&password:(.+)$")
        val match = format.find(url)
        if (match != null) {
            // determine DAV URL from root URL
            try {
                val serverUrl = match.groupValues[1]
                val davPath = requireActivity().intent.getStringExtra(EXTRA_DAV_PATH)
                loginModel.baseURI = if (davPath != null)
                    (serverUrl + davPath).toHttpUrl().toUri()
                else
                    URI.create(serverUrl)

                loginModel.credentials = Credentials(
                        userName = match.groupValues[2],
                        password = match.groupValues[3]
                )

                // continue to next fragment
                parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, DetectConfigurationFragment(), null)
                        .addToBackStack(null)
                        .commit()
            } catch (e: IllegalArgumentException) {
                Logger.log.log(Level.SEVERE, "Couldn't parse server argument of nc URL: $url", e)
            }
        } else
            Logger.log.severe("Unknown format of nc URL: $url")
    }


    inner class CustomWebViewClient: WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, url: String) =
                if (url.startsWith("nc://login")) {
                    Logger.log.fine("Received nc URL: $url")
                    onReceivedNcUrl(url)
                    true
                } else {
                    Logger.log.fine("Didn't handle $url")
                    false
                }

        override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
            Logger.log.warning("Received error (deprecated API) $errorCode $description on $failingUrl")
            showError(view, "$description ($errorCode)")
        }
        @TargetApi(23)
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            Logger.log.warning("Received error ${error.errorCode} on ${request.url}")
            if (request.isForMainFrame)
                showError(view, "${error.description} (${error.errorCode})")
        }
        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            val message = "${errorResponse.statusCode} ${errorResponse.reasonPhrase}"
            Logger.log.warning("Received HTTP error $message on ${request.url}")
            if (request.isForMainFrame)
                showError(view, message)
        }
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            Logger.log.warning("Received TLS error ${error.primaryError} on ${error.url}")
            if (error.url == view.url)
                showError(view, getString(R.string.login_webview_tlserror, error.primaryError))
            handler.cancel()
        }

        fun showError(view: WebView, message: CharSequence) {
            Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.login_webview_retry, { view.reload() })
                    .show()
        }
    }


    class Factory : ILoginCredentialsFragment {

        override fun getFragment(intent: Intent) =
                if (intent.hasExtra(EXTRA_LOGIN_FLOW))
                    NextcloudLoginFlowFragment()
                else
                    null

    }

}
