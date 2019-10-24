package at.bitfire.davdroid.ui.setup

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.Credentials
import okhttp3.HttpUrl
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
        loginModel = ViewModelProviders.of(requireActivity()).get(LoginModel::class.java)

        val webView = WebView(requireActivity())
        webView.settings.apply {
            javaScriptEnabled = true
            userAgentString = BuildConfig.userAgent
        }
        webView.loadUrl(
                requireActivity().intent.data.toString(),   // https://nextcloud.example.com/index.php/login/flow
                mapOf(Pair("OCS-APIREQUEST", "true"))
        )
        webView.webViewClient = object: WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String) =
                    if (url.startsWith("nc://login")) {
                        onReceivedNcUrl(url)
                        true
                    } else {
                        Logger.log.info("Didn't handle $url")
                        false
                    }
        }
        return webView
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
                    HttpUrl.get(serverUrl + davPath).uri()
                else
                    URI.create(serverUrl)

                loginModel.credentials = Credentials(
                        userName = match.groupValues[2],
                        password = match.groupValues[3]
                )

                // continue to next fragment
                requireFragmentManager().beginTransaction()
                        .replace(android.R.id.content, DetectConfigurationFragment(), null)
                        .addToBackStack(null)
                        .commit()
            } catch (e: IllegalArgumentException) {
                Logger.log.log(Level.SEVERE, "Couldn't parse server argument of nc URL: $url", e)
            }
        } else
            Logger.log.severe("Unknown format of nc URL: $url")
    }


    class Factory : ILoginCredentialsFragment {

        override fun getFragment(intent: Intent) =
                if (intent.hasExtra(EXTRA_LOGIN_FLOW))
                    NextcloudLoginFlowFragment()
                else
                    null

    }

}
