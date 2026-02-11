/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.viewinterop.AndroidView
import io.ktor.http.HttpHeaders

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewCompat(
    url: String,
    modifier: Modifier = Modifier,
    layoutParams: ViewGroup.LayoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                this.layoutParams = layoutParams
                
                // Enable JavaScript for are-you-human checks
                settings.javaScriptEnabled = true

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val loadingWeblatePage = request.url.toString().contains("weblate.org")
                        return if (loadingWeblatePage) {
                            false // Load Weblate pages in WebView (Don't override loading)
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW, request.url)
                            context.startActivity(intent) // Open external links in browser
                            true // Abort non-weblate page load in webview
                        }
                    }
                }
                loadUrl(url, mapOf(
                    HttpHeaders.AcceptLanguage to Locale.current.toLanguageTag()
                ))
            }
        }
    )
}