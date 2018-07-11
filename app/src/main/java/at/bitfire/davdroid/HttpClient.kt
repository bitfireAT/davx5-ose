/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid

import android.content.Context
import android.os.Build
import android.security.KeyChain
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.dav4android.BasicDigestAuthHandler
import at.bitfire.dav4android.Constants
import at.bitfire.dav4android.UrlUtils
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.Credentials
import at.bitfire.davdroid.settings.ISettings
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import javax.net.ssl.KeyManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

class HttpClient private constructor(
        val okHttpClient: OkHttpClient,
        private val certManager: CustomCertManager?
): AutoCloseable {

    companion object {
        /** [OkHttpClient] singleton to build all clients from */
        val sharedClient = OkHttpClient.Builder()
                // set timeouts
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)

                // don't allow redirects by default, because it would break PROPFIND handling
                .followRedirects(false)

                // add User-Agent to every request
                .addNetworkInterceptor(UserAgentInterceptor)

                .build()!!
    }

    override fun close() {
        certManager?.close()
    }

    class Builder(
            val context: Context? = null,
            val settings: ISettings? = null,
            accountSettings: AccountSettings? = null,
            val logger: java.util.logging.Logger = Logger.log
    ) {
        private var certManager: CustomCertManager? = null
        private var certificateAlias: String? = null

        private val orig = sharedClient.newBuilder()

        init {
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

            settings?.let {
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

                context?.let {
                    if (BuildConfig.customCerts)
                        customCertManager(CustomCertManager(context, true, !settings.getBoolean(App.DISTRUST_SYSTEM_CERTIFICATES, false)))

                    // use account settings for authentication
                    accountSettings?.let {
                        addAuthentication(null, it.credentials())
                    }
                }
            }
        }

        constructor(context: Context, host: String?, credentials: Credentials): this(context) {
            addAuthentication(host, credentials)
        }

        fun withDiskCache(): Builder {
            val context = context ?: throw IllegalArgumentException("Context is required to find the cache directory")
            for (dir in arrayOf(context.externalCacheDir, context.cacheDir)) {
                if (dir.exists() && dir.canWrite()) {
                    val cacheDir = File(dir, "HttpClient")
                    cacheDir.mkdir()
                    Logger.log.fine("Using disk cache: $cacheDir")
                    orig.cache(Cache(cacheDir, 10*1024*1024))
                    break
                }
            }
            return this
        }

        fun followRedirects(follow: Boolean): Builder {
            orig.followRedirects(follow)
            return this
        }

        fun customCertManager(manager: CustomCertManager) {
            certManager = manager
        }
        fun setForeground(foreground: Boolean): Builder {
            certManager?.appInForeground = foreground
            return this
        }

        fun addAuthentication(host: String?, credentials: Credentials): Builder {
            when (credentials.type) {
                Credentials.Type.UsernamePassword -> {
                    val authHandler = BasicDigestAuthHandler(UrlUtils.hostToDomain(host), credentials.userName!!, credentials.password!!)
                    orig    .addNetworkInterceptor(authHandler)
                            .authenticator(authHandler)
                }
                Credentials.Type.ClientCertificate -> {
                    certificateAlias = credentials.certificateAlias
                }
            }
            return this
        }

        fun build(): HttpClient {
            val trustManager = certManager ?: {
                val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                factory.init(null as KeyStore?)
                factory.trustManagers.first() as X509TrustManager
            }()

            val hostnameVerifier = certManager?.hostnameVerifier(OkHostnameVerifier.INSTANCE)
                    ?: OkHostnameVerifier.INSTANCE

            var keyManager: KeyManager? = null
            try {
                certificateAlias?.let { alias ->
                    // get client certificate and private key
                    val certs = KeyChain.getCertificateChain(context, alias) ?: return@let
                    val key = KeyChain.getPrivateKey(context, alias) ?: return@let
                    logger.fine("Using client certificate $alias for authentication (chain length: ${certs.size})")

                    // create Android KeyStore (performs key operations without revealing secret data to DAVdroid)
                    val keyStore = KeyStore.getInstance("AndroidKeyStore")
                    keyStore.load(null)

                    // create KeyManager
                    keyManager = object: X509ExtendedKeyManager() {
                        override fun getServerAliases(p0: String?, p1: Array<out Principal>?): Array<String>? = null
                        override fun chooseServerAlias(p0: String?, p1: Array<out Principal>?, p2: Socket?) = null

                        override fun getClientAliases(p0: String?, p1: Array<out Principal>?) =
                                arrayOf(alias)

                        override fun chooseClientAlias(p0: Array<out String>?, p1: Array<out Principal>?, p2: Socket?) =
                                alias

                        override fun getCertificateChain(forAlias: String?) =
                                certs.takeIf { forAlias == alias }

                        override fun getPrivateKey(forAlias: String?) =
                                key.takeIf { forAlias == alias }
                    }
                }
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Couldn't set up client certificate authentication", e)
            }

            orig.sslSocketFactory(CustomTlsSocketFactory(keyManager, trustManager), trustManager)
            orig.hostnameVerifier(hostnameVerifier)

            return HttpClient(orig.build(), certManager)
        }

    }


    private object UserAgentInterceptor: Interceptor {

        // use Locale.US because numbers may be encoded as non-ASCII characters in other locales
        private val userAgentDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        private val userAgentDate = userAgentDateFormat.format(Date(BuildConfig.buildTime))
        private val userAgent = "DAVdroid/${BuildConfig.VERSION_NAME} ($userAgentDate; dav4android; okhttp/${Constants.okHttpVersion}) Android/${Build.VERSION.RELEASE}"

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
