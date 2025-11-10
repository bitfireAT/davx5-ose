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
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.ForegroundTracker
import com.google.common.net.HttpHeaders
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import okhttp3.Authenticator
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.brotli.BrotliInterceptor
import okhttp3.internal.tls.OkHostnameVerifier
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Builder for the [OkHttpClient].
 *
 * **Attention:** If the builder is injected, it shouldn't be used from multiple locations to generate different clients because then
 * there's only one [HttpClientBuilder] object and setting properties from one location would influence the others.
 *
 * To generate multiple clients, inject and use `Provider<HttpClientBuilder>` instead.
 */
class HttpClientBuilder @Inject constructor(
    private val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext private val context: Context,
    defaultLogger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val keyManagerFactory: ClientCertKeyManager.Factory,
    private val oAuthInterceptorFactory: OAuthInterceptor.Factory,
    private val settingsManager: SettingsManager
) {

    companion object {
        init {
            // make sure Conscrypt is available when the HttpClientBuilder class is loaded the first time
            ConscryptIntegration().initialize()
        }
    }

    /**
     * Flag to prevent multiple [build] calls
     */
    var alreadyBuilt = false

    // property setters/getters

    private var logger: Logger = defaultLogger
    fun setLogger(logger: Logger): HttpClientBuilder {
        this.logger = logger
        return this
    }

    private var loggerInterceptorLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY

    fun loggerInterceptorLevel(level: HttpLoggingInterceptor.Level): HttpClientBuilder {
        loggerInterceptorLevel = level
        return this
    }

    // default cookie store for non-persistent cookies (some services like Horde use cookies for session tracking)
    private var cookieStore: CookieJar = MemoryCookieStore()

    fun setCookieStore(cookieStore: CookieJar): HttpClientBuilder {
        this.cookieStore = cookieStore
        return this
    }

    private var authenticationInterceptor: Interceptor? = null
    private var authenticator: Authenticator? = null
    private var certificateAlias: String? = null

    fun authenticate(host: String?, getCredentials: () -> Credentials, updateAuthState: ((AuthState) -> Unit)? = null): HttpClientBuilder {
        val credentials = getCredentials()
        if (credentials.authState != null) {
            // OAuth
            authenticationInterceptor = oAuthInterceptorFactory.create(
                readAuthState = {
                    // We don't use the "credentials" object from above because it may contain an outdated access token
                    // when readAuthState is called. Instead, we fetch the up-to-date auth-state.
                    getCredentials().authState
                },
                writeAuthState = { authState ->
                    updateAuthState?.invoke(authState)
                }

            )

        } else if (credentials.username != null && credentials.password != null) {
            // basic/digest auth
            val authHandler = BasicDigestAuthHandler(
                domain = UrlUtils.hostToDomain(host),
                username = credentials.username,
                password = credentials.password.asCharArray(),
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

    fun followRedirects(follow: Boolean): HttpClientBuilder {
        followRedirects = follow
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
    fun fromAccount(account: Account, onlyHost: String? = null): HttpClientBuilder {
        val accountSettings = accountSettingsFactory.create(account)
        authenticate(
            host = onlyHost,
            getCredentials = {
                accountSettings.credentials()
            },
            updateAuthState = { authState ->
                accountSettings.updateAuthState(authState)
            }
        )
        return this
    }

    /**
     * Same as [fromAccount], but can be called on any thread.
     *
     * @throws at.bitfire.davdroid.sync.account.InvalidAccountException     when the account doesn't exist
     */
    suspend fun fromAccountAsync(account: Account, onlyHost: String? = null): HttpClientBuilder = withContext(ioDispatcher) {
        fromAccount(account, onlyHost)
    }


    // actual builder

    /**
     * Builds the [OkHttpClient].
     *
     * Must be called only once because multiple calls indicate this wrong usage pattern:
     *
     * ```
     * val builder = HttpClientBuilder(/*injected*/)
     * val client1 = builder.configure().builder()
     * val client2 = builder.configureOtherwise().builder()
     * ```
     *
     * However in this case the configuration of `client1` is still in `builder` and would be reused for `client2`,
     * which is usually not desired.
     *
     * @throws IllegalStateException    on second and later calls
     */
    fun build(): OkHttpClient {
        if (alreadyBuilt)
            throw IllegalStateException("build() must only be called once; use Provider<HttpClientBuilder>")

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
                ConnectionSpec.Companion.CLEARTEXT,
                ConnectionSpec.Companion.MODERN_TLS
            ))

            // offer Brotli and gzip compression (can be disabled per request with `Accept-Encoding: identity`)
            .addInterceptor(BrotliInterceptor)

        // app-wide custom proxy support
        buildProxy(okBuilder)

        // add authentication and connection security (including client certificates9
        buildAuthentication(okBuilder)
        buildConnectionSecurity(okBuilder)

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

        alreadyBuilt = true
        return okBuilder.build()
    }

    private fun buildAuthentication(okBuilder: OkHttpClient.Builder) {
        // basic/digest auth and OAuth
        authenticationInterceptor?.let { okBuilder.addInterceptor(it) }
        authenticator?.let { okBuilder.authenticator(it) }
    }

    private fun buildConnectionSecurity(okBuilder: OkHttpClient.Builder) {
        // client certificate
        val clientKeyManager: KeyManager? = certificateAlias?.let { alias ->
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

        // trust manager (depending on whether custom certificates are allowed)
        val customTrustManager: X509TrustManager?
        val customHostnameVerifier: HostnameVerifier?

        if (BuildConfig.allowCustomCerts) {
            // use cert4android for custom certificate handling
            customTrustManager = CustomCertManager(
                context = context,
                trustSystemCerts = !settingsManager.getBoolean(Settings.DISTRUST_SYSTEM_CERTIFICATES),
                appInForeground = ForegroundTracker.inForeground
            )
            // allow users to accept certificates with wrong host names
            customHostnameVerifier = customTrustManager.HostnameVerifier(OkHostnameVerifier)

        } else {
            // no custom certificates, use default trust manager and hostname verifier
            customTrustManager = null
            customHostnameVerifier = null
        }

        // change settings only if we have at least only one cust component
        if (clientKeyManager != null || customTrustManager != null) {
            val trustManager = customTrustManager ?: defaultTrustManager()

            // use trust manager and client key manager (if defined) for TLS connections
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(
                /* km = */ if (clientKeyManager != null) arrayOf(clientKeyManager) else null,
                /* tm = */ arrayOf(trustManager),
                /* random = */ null
            )
            okBuilder.sslSocketFactory(sslContext.socketFactory, trustManager)
        }

        // also add the custom hostname verifier (if defined)
        if (customHostnameVerifier != null)
            okBuilder.hostnameVerifier(customHostnameVerifier)
    }

    private fun defaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
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