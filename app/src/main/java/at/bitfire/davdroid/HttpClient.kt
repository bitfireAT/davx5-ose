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
import at.bitfire.dav4android.BasicDigestAuthHandler
import at.bitfire.dav4android.UrlUtils
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.Settings
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetSocketAddress
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class HttpClient private constructor() {

    companion object {

        private val client = OkHttpClient()
        private val userAgentInterceptor = UserAgentInterceptor()

        private val productName = when(BuildConfig.FLAVOR) {
            App.FLAVOR_ICLOUD  -> "MultiSync for Cloud"
            App.FLAVOR_SOLDUPE -> "Soldupe Sync"
            else               -> "DAVdroid"
        }
        private val userAgentDate = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(BuildConfig.buildTime))
        private val userAgent = "$productName/${BuildConfig.VERSION_NAME} ($userAgentDate; dav4android; okhttp3) Android/${Build.VERSION.RELEASE}"


        @JvmStatic
        @JvmOverloads
        fun create(context: Context?, settings: AccountSettings? = null, logger: java.util.logging.Logger = Logger.log): OkHttpClient {
            var builder = defaultBuilder(context, logger)

            // use account settings for authentication
            settings?.let {
                val userName = it.username()
                val password = it.password()
                if (userName != null && password != null)
                    builder = addAuthentication(builder, null, userName, password)
            }

            return builder.build()
        }

        @JvmStatic
        @Throws(InvalidAccountException::class)
        fun create(context: Context, account: Account) =
            create(context, AccountSettings(context, account))


        private fun defaultBuilder(context: Context?, logger: java.util.logging.Logger): OkHttpClient.Builder {
            val builder = client.newBuilder()

            // use MemorizingTrustManager to manage self-signed certificates
            if (CustomCertificates.sslSocketFactoryCompat != null && CustomCertificates.certManager != null)
                builder.sslSocketFactory(CustomCertificates.sslSocketFactoryCompat, CustomCertificates.certManager)
            CustomCertificates.hostnameVerifier?.let { builder.hostnameVerifier(it) }

            // set timeouts
            builder.connectTimeout(30, TimeUnit.SECONDS)
            builder.writeTimeout(30, TimeUnit.SECONDS)
            builder.readTimeout(120, TimeUnit.SECONDS)

            // don't allow redirects, because it would break PROPFIND handling
            builder.followRedirects(false)

            // custom proxy support
            context?.let {
                ServiceDB.OpenHelper(it).use { dbHelper ->
                    try {
                        val settings = Settings(dbHelper.readableDatabase)
                        if (settings.getBoolean(App.OVERRIDE_PROXY, false)) {
                            val address = InetSocketAddress(
                                    settings.getString(App.OVERRIDE_PROXY_HOST, App.OVERRIDE_PROXY_HOST_DEFAULT),
                                    settings.getInt(App.OVERRIDE_PROXY_PORT, App.OVERRIDE_PROXY_PORT_DEFAULT)
                            )

                            val proxy = Proxy(Proxy.Type.HTTP, address)
                            builder.proxy(proxy)
                            Logger.log.log(Level.INFO, "Using proxy", proxy)
                        }
                    } catch(e: Exception) {
                        Logger.log.log(Level.SEVERE, "Can't set proxy, ignoring", e)
                    }
                }
            }

            // add User-Agent to every request
            builder.addNetworkInterceptor(userAgentInterceptor)

            // add cookie store for non-persistent cookies (some services like Horde use cookies for session tracking)
            builder.cookieJar(MemoryCookieStore())

            // add network logging, if requested
            if (logger.isLoggable(Level.FINEST)) {
                val loggingInterceptor = HttpLoggingInterceptor(HttpLoggingInterceptor.Logger {
                    message -> logger.finest(message)
                })
                loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
                builder.addInterceptor(loggingInterceptor)
            }

            return builder
        }

        fun addAuthentication(builder: OkHttpClient.Builder, host: String?, username: String, password: String): OkHttpClient.Builder {
            val authHandler = BasicDigestAuthHandler(UrlUtils.hostToDomain(host), username, password);
            return builder
                    .addNetworkInterceptor(authHandler)
                    .authenticator(authHandler)
        }

        @JvmOverloads
        @JvmStatic
        fun addAuthentication(client: OkHttpClient, host: String? = null, username: String, password: String): OkHttpClient {
            val builder = client.newBuilder()
            addAuthentication(builder, host, username, password)
            return builder.build()
        }

    }


    private class UserAgentInterceptor: Interceptor {

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
