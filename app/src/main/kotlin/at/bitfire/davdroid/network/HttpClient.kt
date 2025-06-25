/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.accounts.Account
import android.content.Context
import androidx.annotation.WorkerThread
import at.bitfire.cert4android.CustomCertManager
import at.bitfire.dav4jvm.BasicDigestAuthHandler
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.ForegroundTracker
import com.google.common.net.HttpHeaders
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.brotli.BrotliInterceptor
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext

class HttpClient(
    val okHttpClient: OkHttpClient,
    private val authorizationService: AuthorizationService? = null
): AutoCloseable {

    override fun close() {
        authorizationService?.dispose()
        okHttpClient.cache?.close()
    }


    // builder

    /**
     * Builder for the [HttpClient].
     *
     * **Attention:** If the builder is injected, it shouldn't be used from multiple locations to generate different clients because then
     * there's only one [Builder] object and setting properties from one location would influence the others.
     *
     * To generate multiple clients, inject and use `Provider<HttpClient.Builder>` instead.
     */
    class Builder @Inject constructor(
        private val accountSettingsFactory: AccountSettings.Factory,
        private val authorizationServiceProvider: Provider<AuthorizationService>,
        @ApplicationContext private val context: Context,
        defaultLogger: Logger,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val keyManagerFactory: ClientCertKeyManager.Factory,
        private val oAuthInterceptorFactory: OAuthInterceptor.Factory,
        private val settingsManager: SettingsManager
    ) {

        // property setters/getters

        private var logger: Logger = defaultLogger
        fun setLogger(logger: Logger): Builder {
            this.logger = logger
            return this
        }

        private var loggerInterceptorLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY
        fun loggerInterceptorLevel(level: HttpLoggingInterceptor.Level): Builder {
            loggerInterceptorLevel = level
            return this
        }

        // default cookie store for non-persistent cookies (some services like Horde use cookies for session tracking)
        private var cookieStore: CookieJar = MemoryCookieStore()
        fun setCookieStore(cookieStore: CookieJar): Builder {
            this.cookieStore = cookieStore
            return this
        }

        private var authenticationInterceptor: Interceptor? = null
        private var authenticator: Authenticator? = null
        private var authorizationService: AuthorizationService? = null
        private var certificateAlias: String? = null
        fun authenticate(host: String?, credentials: Credentials, authStateCallback: OAuthInterceptor.AuthStateUpdateCallback? = null): Builder {
            if (credentials.authState != null) {
                // OAuth
                val authService = authorizationService ?: authorizationServiceProvider.get()
                authenticationInterceptor = oAuthInterceptorFactory.create(authService, credentials.authState, authStateCallback)
                authorizationService = authService

            } else if (credentials.username != null && credentials.password != null) {
                // basic/digest auth
                val authHandler = BasicDigestAuthHandler(
                    domain = UrlUtils.hostToDomain(host),
                    username = credentials.username,
                    password = credentials.password,
                    insecurePreemptive = true
                )
                authenticationInterceptor = authHandler
                authenticator = authHandler
            }

            // client certificate
            if (credentials.certificateAlias != null)
                certificateAlias = credentials.certificateAlias

            return this
        }

        private var followRedirects = false
        fun followRedirects(follow: Boolean): Builder {
            followRedirects = follow
            return this
        }

        private var cache: Cache? = null
        @Suppress("unused")
        fun withDiskCache(maxSize: Long = 10*1024*1024): Builder {
            for (dir in arrayOf(context.externalCacheDir, context.cacheDir).filterNotNull()) {
                if (dir.exists() && dir.canWrite()) {
                    val cacheDir = File(dir, "HttpClient")
                    cacheDir.mkdir()
                    logger.fine("Using disk cache: $cacheDir")
                    cache = Cache(cacheDir, maxSize)
                    break
                }
            }
            return this
        }


        // convenience builders from other classes

        /**
         * Takes authentication (basic/digest or OAuth and client certificate) from a given account.
         *
         * **Must not be run on main thread, because it creates [AccountSettings]!** Use [fromAccountAsync] if possible.
         *
         * @param account   the account to take authentication from
         * @param onlyHost  if set: only authenticate for this host name
         *
         * @throws at.bitfire.davdroid.sync.account.InvalidAccountException     when the account doesn't exist
         */
        @WorkerThread
        fun fromAccount(account: Account, onlyHost: String? = null): Builder {
            val accountSettings = accountSettingsFactory.create(account)
            authenticate(
                host = onlyHost,
                credentials = accountSettings.credentials(),
                authStateCallback = { authState: AuthState ->
                    accountSettings.credentials(Credentials(authState = authState))
                }
            )
            return this
        }

        /**
         * Same as [fromAccount], but can be called on any thread.
         *
         * @throws at.bitfire.davdroid.sync.account.InvalidAccountException     when the account doesn't exist
         */
        suspend fun fromAccountAsync(account: Account, onlyHost: String? = null): Builder = withContext(ioDispatcher) {
            fromAccount(account, onlyHost)
        }


        // actual builder

        fun build(): HttpClient {
            val okBuilder = OkHttpClient.Builder()
                // Set timeouts. According to [AbstractThreadedSyncAdapter], when there is no network
                // traffic within a minute, a sync will be cancelled.
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .pingInterval(45, TimeUnit.SECONDS)     // avoid cancellation because of missing traffic; only works for HTTP/2

                // don't allow redirects by default because it would break PROPFIND handling
                .followRedirects(followRedirects)

                // add User-Agent to every request
                .addInterceptor(UserAgentInterceptor)

                // connection-private cookie store
                .cookieJar(cookieStore)

                // allow cleartext and TLS 1.2+
                .connectionSpecs(listOf(
                    ConnectionSpec.CLEARTEXT,
                    ConnectionSpec.MODERN_TLS
                ))

                // offer Brotli and gzip compression (can be disabled per request with `Accept-Encoding: identity`)
                .addInterceptor(BrotliInterceptor)

                // add cache, if requested
                .cache(cache)

            // app-wide custom proxy support
            buildProxy(okBuilder)

            // add authentication
            buildAuthentication(okBuilder)

            // add network logging, if requested
            if (logger.isLoggable(Level.FINEST)) {
                val loggingInterceptor = HttpLoggingInterceptor { message -> logger.finest(message) }
                loggingInterceptor.redactHeader(HttpHeaders.AUTHORIZATION)
                loggingInterceptor.redactHeader(HttpHeaders.COOKIE)
                loggingInterceptor.redactHeader(HttpHeaders.SET_COOKIE)
                loggingInterceptor.redactHeader(HttpHeaders.SET_COOKIE2)
                loggingInterceptor.level = loggerInterceptorLevel
                okBuilder.addNetworkInterceptor(loggingInterceptor)
            }

            return HttpClient(
                okHttpClient = okBuilder.build(),
                authorizationService = authorizationService
            )
        }

        private fun buildAuthentication(okBuilder: OkHttpClient.Builder) {
            // basic/digest auth and OAuth
            authenticationInterceptor?.let { okBuilder.addInterceptor(it) }
            authenticator?.let { okBuilder.authenticator(it) }

            // client certificate
            val keyManager: KeyManager? = certificateAlias?.let { alias ->
                try {
                    val manager = keyManagerFactory.create(alias)
                    logger.fine("Using certificate $alias for authentication")

                    // HTTP/2 doesn't support client certificates (yet)
                    // see https://tools.ietf.org/html/draft-ietf-httpbis-http2-secondary-certs-04
                    okBuilder.protocols(listOf(Protocol.HTTP_1_1))

                    manager
                } catch (e: IllegalArgumentException) {
                    logger.log(Level.SEVERE, "Couldn't create KeyManager for certificate $alias", e)
                    null
                }
            }

            // cert4android integration
            val certManager = CustomCertManager(
                context = context,
                trustSystemCerts = !settingsManager.getBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES),
                appInForeground = if (/* davx5-ose */ true)
                    ForegroundTracker.inForeground  // interactive mode
                else
                    null                            // non-interactive mode
            )

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(
                /* km = */ if (keyManager != null) arrayOf(keyManager) else null,
                /* tm = */ arrayOf(certManager),
                /* random = */ null
            )
            okBuilder
                .sslSocketFactory(sslContext.socketFactory, certManager)
                .hostnameVerifier(certManager.HostnameVerifier(OkHostnameVerifier))
        }

        private fun buildProxy(okBuilder: OkHttpClient.Builder) {
            try {
                val proxyTypeValue = settingsManager.getInt(Settings.PROXY_TYPE)
                if (proxyTypeValue != Settings.PROXY_TYPE_SYSTEM) {
                    // we set our own proxy
                    val address by lazy {           // lazy because not required for PROXY_TYPE_NONE
                        InetSocketAddress(
                            settingsManager.getString(Settings.PROXY_HOST),
                            settingsManager.getInt(Settings.PROXY_PORT)
                        )
                    }
                    val proxy =
                        when (proxyTypeValue) {
                            Settings.PROXY_TYPE_NONE -> Proxy.NO_PROXY
                            Settings.PROXY_TYPE_HTTP -> Proxy(Proxy.Type.HTTP, address)
                            Settings.PROXY_TYPE_SOCKS -> Proxy(Proxy.Type.SOCKS, address)
                            else -> throw IllegalArgumentException("Invalid proxy type")
                        }
                    okBuilder.proxy(proxy)
                    logger.log(Level.INFO, "Using proxy setting", proxy)
                }
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Can't set proxy, ignoring", e)
            }
        }

    }

}