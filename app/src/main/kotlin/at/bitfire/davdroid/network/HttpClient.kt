/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.content.Context
import android.os.Build
import android.security.KeyChain
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.dav4jvm.BasicDigestAuthHandler
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import okhttp3.Cache
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

class HttpClient @AssistedInject constructor(
    @Assisted val okHttpClient: OkHttpClient,
    @Assisted private var authService: AuthorizationService? = null,
    val settingsManager: SettingsManager
): AutoCloseable {

    companion object {
        /** max. size of disk cache (10 MB) */
        const val DISK_CACHE_MAX_SIZE: Long = 10*1024*1024

        /** Base Builder to build all clients from. Use rarely; [OkHttpClient]s should
         * be reused as much as possible. */
        fun baseBuilder() =
            OkHttpClient.Builder()
                // Set timeouts. According to [AbstractThreadedSyncAdapter], when there is no network
                // traffic within a minute, a sync will be cancelled.
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .pingInterval(
                    45,
                    TimeUnit.SECONDS
                )     // avoid cancellation because of missing traffic; only works for HTTP/2

                // keep TLS 1.0 and 1.1 for now; remove when major browsers have dropped it (probably 2020)
                .connectionSpecs(
                    listOf(
                        ConnectionSpec.CLEARTEXT,
                        ConnectionSpec.COMPATIBLE_TLS
                    )
                )

                // don't allow redirects by default, because it would break PROPFIND handling
                .followRedirects(false)

                // add User-Agent to every request
                .addInterceptor(UserAgentInterceptor)
    }

    @AssistedFactory
    interface Factory {
        fun create(okHttpClient: OkHttpClient, authService: AuthorizationService?): HttpClient
    }


    override fun close() {
        authService?.dispose()
        okHttpClient.cache?.close()
    }


    class Builder(
        val context: Context,
        accountSettings: AccountSettings? = null,
        val logger: java.util.logging.Logger? = Logger.log,
        val loggerLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY
    ) {

        @EntryPoint
        @InstallIn(SingletonComponent::class)
        interface HttpClientBuilderEntryPoint {
            fun authorizationService(): AuthorizationService
            fun httpClientFactory(): HttpClient.Factory
            fun settingsManager(): SettingsManager
        }

        private val entryPoint = EntryPointAccessors.fromApplication<HttpClientBuilderEntryPoint>(context)

        fun interface CertManagerProducer {
            fun certManager(): CustomCertManager
        }

        private var appInForeground: MutableStateFlow<Boolean>? =
                MutableStateFlow(false)
        private var authService: AuthorizationService? = null
        private var certManagerProducer: CertManagerProducer? = null
        private var certificateAlias: String? = null
        private var offerCompression: Boolean = false

        // default cookie store for non-persistent cookies (some services like Horde use cookies for session tracking)
        private var cookieStore: CookieJar? = MemoryCookieStore()

        private val orig = baseBuilder()

        init {
            // add network logging, if requested
            if (logger != null && logger.isLoggable(Level.FINEST)) {
                val loggingInterceptor = HttpLoggingInterceptor { message -> logger.finest(message) }
                loggingInterceptor.level = loggerLevel
                orig.addNetworkInterceptor(loggingInterceptor)
            }

            val settings = entryPoint.settingsManager()

            // custom proxy support
            try {
                val proxyTypeValue = settings.getInt(Settings.PROXY_TYPE)
                if (proxyTypeValue != Settings.PROXY_TYPE_SYSTEM) {
                    // we set our own proxy
                    val address by lazy {           // lazy because not required for PROXY_TYPE_NONE
                        InetSocketAddress(
                            settings.getString(Settings.PROXY_HOST),
                            settings.getInt(Settings.PROXY_PORT)
                        )
                    }
                    val proxy =
                        when (proxyTypeValue) {
                            Settings.PROXY_TYPE_NONE -> Proxy.NO_PROXY
                            Settings.PROXY_TYPE_HTTP -> Proxy(Proxy.Type.HTTP, address)
                            Settings.PROXY_TYPE_SOCKS -> Proxy(Proxy.Type.SOCKS, address)
                            else -> throw IllegalArgumentException("Invalid proxy type")
                        }
                    orig.proxy(proxy)
                    Logger.log.log(Level.INFO, "Using proxy setting", proxy)
                }
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Can't set proxy, ignoring", e)
            }

            customCertManager {
                // by default, use a CustomCertManager that respects the "distrust system certificates" setting
                val trustSystemCerts = !settings.getBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES)
                CustomCertManager(context, trustSystemCerts, appInForeground)
            }

            // use account settings for authentication and cookies
            if (accountSettings != null)
                addAuthentication(null, accountSettings.credentials(), authStateCallback = { authState: AuthState ->
                    accountSettings.credentials(Credentials(authState = authState))
                })
        }

        constructor(context: Context, host: String?, credentials: Credentials?) : this(context) {
            if (credentials != null)
                addAuthentication(host, credentials)
        }

        fun addAuthentication(host: String?, credentials: Credentials, insecurePreemptive: Boolean = false, authStateCallback: BearerAuthInterceptor.AuthStateUpdateCallback? = null): Builder {
            if (credentials.username != null && credentials.password != null) {
                val authHandler = BasicDigestAuthHandler(UrlUtils.hostToDomain(host), credentials.username, credentials.password, insecurePreemptive)
                orig.addNetworkInterceptor(authHandler)
                    .authenticator(authHandler)
            }

            if (credentials.certificateAlias != null)
                certificateAlias = credentials.certificateAlias

            credentials.authState?.let { authState ->
                val newAuthService = entryPoint.authorizationService()
                authService = newAuthService
                BearerAuthInterceptor.fromAuthState(newAuthService, authState, authStateCallback)?.let { bearerAuthInterceptor ->
                    orig.addNetworkInterceptor(bearerAuthInterceptor)
                }
            }
            return this
        }

        fun allowCompression(allow: Boolean): Builder {
            offerCompression = allow
            return this
        }

        fun cookieStore(store: CookieJar?): Builder {
            cookieStore = store
            return this
        }

        fun followRedirects(follow: Boolean): Builder {
            orig.followRedirects(follow)
            return this
        }

        fun customCertManager(producer: CertManagerProducer) {
            certManagerProducer = producer
        }
        fun setForeground(foreground: Boolean): Builder {
            appInForeground?.value = foreground
            return this
        }

        fun withDiskCache(): Builder {
            for (dir in arrayOf(context.externalCacheDir, context.cacheDir).filterNotNull()) {
                if (dir.exists() && dir.canWrite()) {
                    val cacheDir = File(dir, "HttpClient")
                    cacheDir.mkdir()
                    Logger.log.fine("Using disk cache: $cacheDir")
                    orig.cache(Cache(cacheDir, DISK_CACHE_MAX_SIZE))
                    break
                }
            }
            return this
        }

        fun build(): HttpClient {
            cookieStore?.let {
                orig.cookieJar(it)
            }

            if (offerCompression)
                // offer Brotli and gzip compression
                orig.addInterceptor(BrotliInterceptor)

            var keyManager: KeyManager? = null
            certificateAlias?.let { alias ->
                // get provider certificate and private key
                val certs = KeyChain.getCertificateChain(context, alias) ?: return@let
                val key = KeyChain.getPrivateKey(context, alias) ?: return@let
                logger?.fine("Using provider certificate $alias for authentication (chain length: ${certs.size})")

                // create KeyManager
                keyManager = object : X509ExtendedKeyManager() {
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

                // HTTP/2 doesn't support client certificates (yet)
                // see https://tools.ietf.org/html/draft-ietf-httpbis-http2-secondary-certs-04
                orig.protocols(listOf(Protocol.HTTP_1_1))
            }

            if (certManagerProducer != null || keyManager != null) {
                val manager = certManagerProducer?.certManager()

                val trustManager = manager ?: /* fall back to system default trust manager */
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    .let { factory ->
                        factory.init(null as KeyStore?)
                        factory.trustManagers.first() as X509TrustManager
                    }

                val hostnameVerifier =
                    if (manager != null)
                        manager.HostnameVerifier(OkHostnameVerifier)
                    else
                        OkHostnameVerifier

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(
                    if (keyManager != null) arrayOf(keyManager) else null,
                    arrayOf(trustManager),
                    null)
                orig.sslSocketFactory(sslContext.socketFactory, trustManager)
                orig.hostnameVerifier(hostnameVerifier)
            }

            return entryPoint.httpClientFactory().create(orig.build(), authService = authService)
        }

    }


    object UserAgentInterceptor: Interceptor {

        // use Locale.ROOT because numbers may be encoded as non-ASCII characters in other locales
        private val userAgentDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT)
        private val userAgentDate = userAgentDateFormat.format(Date(BuildConfig.buildTime))
        val userAgent = "${BuildConfig.userAgent}/${BuildConfig.VERSION_NAME} ($userAgentDate; dav4jvm; " +
                "okhttp/${OkHttp.VERSION}) Android/${Build.VERSION.RELEASE}"

        init {
            Logger.log.info("Will set \"User-Agent: $userAgent\" for further requests")
        }

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
