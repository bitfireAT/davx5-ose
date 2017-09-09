/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.accounts.Account
import android.content.Context
import android.os.Build
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.dav4android.BasicDigestAuthHandler
import at.bitfire.dav4android.UrlUtils
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.Settings
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class HttpClient private constructor(
        val okHttpClient: OkHttpClient,
        private val certManager: CustomCertManager?
): Closeable {

    override fun close() {
        certManager?.close()
    }


    class Builder @JvmOverloads constructor(
            val context: Context? = null,
            accountSettings: AccountSettings? = null,
            logger: java.util.logging.Logger = Logger.log
    ) {
        var certManager: CustomCertManager? = null
        private val orig = OkHttpClient.Builder()

        init {
            // set timeouts
            orig.connectTimeout(30, TimeUnit.SECONDS)
            orig.writeTimeout(30, TimeUnit.SECONDS)
            orig.readTimeout(120, TimeUnit.SECONDS)

            // don't allow redirects by default, because it would break PROPFIND handling
            orig.followRedirects(false)

            // add User-Agent to every request
            orig.addNetworkInterceptor(UserAgentInterceptor)

            // add cookie store for non-persistent cookies (some services like Horde use cookies for session tracking)
            orig.cookieJar(MemoryCookieStore())

            // add network logging, if requested
            if (logger.isLoggable(Level.FINEST)) {
                val loggingInterceptor = HttpLoggingInterceptor(HttpLoggingInterceptor.Logger {
                    message -> logger.finest(message)
                })
                loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
                orig.addInterceptor(loggingInterceptor)
            }

            context?.let {
                ServiceDB.OpenHelper(context).use { dbHelper ->
                    val settings = Settings(dbHelper.readableDatabase)

                    // custom proxy support
                    try {
                        if (settings.getBoolean(App.OVERRIDE_PROXY, false)) {
                            val address = InetSocketAddress(
                                    settings.getString(App.OVERRIDE_PROXY_HOST, App.OVERRIDE_PROXY_HOST_DEFAULT),
                                    settings.getInt(App.OVERRIDE_PROXY_PORT, App.OVERRIDE_PROXY_PORT_DEFAULT)
                            )

                            val proxy = Proxy(Proxy.Type.HTTP, address)
                            orig.proxy(proxy)
                            Logger.log.log(Level.INFO, "Using proxy", proxy)
                        }
                    } catch(e: Exception) {
                        Logger.log.log(Level.SEVERE, "Can't set proxy, ignoring", e)
                    }

                    // use cert4android to manage self-signed certificates
                    if (BuildConfig.customCerts)
                        customCertManager(CustomCertManager(context, !settings.getBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, false)))
                }
            }

            // use account settings for authentication
            accountSettings?.let {
                val userName = it.username()
                val password = it.password()
                if (userName != null && password != null)
                    addAuthentication(null, userName, password)
            }
        }

        constructor(context: Context, account: Account): this(context, AccountSettings(context, account))

        constructor(context: Context, host: String?, username: String, password: String): this(context) {
            addAuthentication(host, username, password)
        }

        fun followRedirects(follow: Boolean) { orig.followRedirects(follow) }

        fun customCertManager(manager: CustomCertManager) {
            certManager = manager
            orig.sslSocketFactory(SSLSocketFactoryCompat(manager), manager)
            orig.hostnameVerifier(manager.hostnameVerifier(OkHostnameVerifier.INSTANCE))
        }
        fun setForeground(foreground: Boolean): Builder {
            certManager?.appInForeground = foreground
            return this
        }

        fun addAuthentication(host: String?, username: String, password: String): Builder {
            val authHandler = BasicDigestAuthHandler(UrlUtils.hostToDomain(host), username, password)
            orig    .addNetworkInterceptor(authHandler)
                    .authenticator(authHandler)
            return this
        }

        fun build() = HttpClient(orig.build(), certManager)
    }


    private object UserAgentInterceptor: Interceptor {

        private val productName = when(BuildConfig.FLAVOR) {
            App.FLAVOR_ICLOUD  -> "MultiSync for Cloud"
            App.FLAVOR_SOLDUPE -> "Soldupe Sync"
            else               -> "DAVdroid"
        }
        private val userAgentDate = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(BuildConfig.buildTime))
        private val userAgent = "$productName/${BuildConfig.VERSION_NAME} ($userAgentDate; dav4android; okhttp3) Android/${Build.VERSION.RELEASE}"

        override fun intercept(chain: Interceptor.Chain): Response {
            val locale = Locale.getDefault()
            val request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", "${locale.language}-${locale.country}, ${locale.language};q=0.7, *;q=0.5")
                    .build()
            return chain.proceed(request)
        }

    }

}
