/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.accounts.Account
import androidx.annotation.WorkerThread
import at.bitfire.dav4jvm.okhttp.BasicDigestAuthHandler
import at.bitfire.dav4jvm.okhttp.UrlUtils
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import com.google.errorprone.annotations.MustBeClosed
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import okhttp3.Authenticator
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import com.google.common.net.HttpHeaders as OkHttpHeaders

/**
 * Builder for the HTTP client.
 *
 * **Attention:** If the builder is injected, it shouldn't be used from multiple locations to generate different clients because then
 * there's only one [HttpClientBuilder] object and setting properties from one location would influence the others.
 *
 * To generate multiple clients, inject and use `Provider<HttpClientBuilder>` instead.
 */
class HttpClientBuilder @Inject constructor(
    private val accountSettingsFactory: AccountSettings.Factory,
    private val connectionSecurityManager: ConnectionSecurityManager,
    defaultLogger: Logger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val oAuthInterceptorFactory: OAuthInterceptor.Factory,
    private val settingsManager: SettingsManager,
    private val productIds: ProductIds,
    private val userAgentInterceptor: UserAgentInterceptor
) {

    companion object {

        init {
            // make sure Conscrypt is available when the HttpClientBuilder class is loaded the first time
            ConscryptIntegration().initialize()
        }

        /**
         * According to [OkHttpClient] documentation, [OkHttpClient]s should be shared, which allows it to use a
         * shared connection and thread pool.
         *
         * We need custom settings for each actual client, but we can use a shared client as a base. This also
         * enables sharing resources like connection and thread pool.
         *
         * The shared client is available for the lifetime of the application and must not be shut down or
         * closed (which is not necessary, according to its documentation).
         */
        val sharedOkHttpClient = OkHttpClient.Builder().apply {
            configureTimeouts(this)
        }.build()

        private fun configureTimeouts(okBuilder: OkHttpClient.Builder) {
            okBuilder
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .pingInterval(45, TimeUnit.SECONDS)     // avoid cancellation because of missing traffic; only works for HTTP/2
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

    // LogLevel.ALL logs headers + body (unlike LogLevel.BODY, which omits headers)
    private var loggerInterceptorLevel: LogLevel = LogLevel.ALL

    fun loggerInterceptorLevel(level: LogLevel): HttpClientBuilder {
        loggerInterceptorLevel = level
        return this
    }

    private var authenticationInterceptor: Interceptor? = null
    private var authenticator: Authenticator? = null
    private var certificateAlias: String? = null

    fun authenticate(domain: String?, getCredentials: () -> Credentials, updateAuthState: ((AuthState) -> Unit)? = null): HttpClientBuilder {
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
                domain = domain,
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
     * @param account       the account to take authentication from
     * @param authDomain    (optional) Send credentials only for the hosts of the given domain. Can be:
     *
     * - a full host name (`caldav.example.com`): then credentials are only sent for the domain of that host name (`example.com`), or
     * - a domain name (`example.com`): then credentials are only sent for the given domain, or
     * - or _null_: then credentials are always sent, regardless of the resource host name.
     *
     * @throws at.bitfire.davdroid.sync.account.InvalidAccountException     when the account doesn't exist
     */
    @WorkerThread
    fun fromAccount(account: Account, authDomain: String? = null): HttpClientBuilder {
        val accountSettings = accountSettingsFactory.create(account)
        authenticate(
            domain = UrlUtils.hostToDomain(authDomain),
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


    // okhttp builder

    /**
     * Builds an [OkHttpClient] with the configured settings.
     *
     * [build] or [buildKtor] is usually called only once because multiple calls indicate this wrong usage pattern:
     *
     * ```
     * val builder = HttpClientBuilder(/*injected*/)
     * val client1 = builder.configure().build()
     * val client2 = builder.configureOtherwise().build()
     * ```
     *
     * However in this case the configuration of `client1` is still in `builder` and would be reused for `client2`,
     * which is usually not desired.
     *
     * Closing/shutting down the client is not necessary.
     */
    @Deprecated("Use buildKtor instead")
    fun build(): OkHttpClient {
        if (alreadyBuilt)
            logger.warning("build() should only be called once; use Provider<HttpClientBuilder> instead")

        val builder = sharedOkHttpClient.newBuilder()
        configureOkHttp(builder)

        alreadyBuilt = true
        return builder.build()
    }

    private fun configureOkHttp(builder: OkHttpClient.Builder) {
        // don't allow redirects by default because it would break PROPFIND handling
        builder.followRedirects(followRedirects)

        // add User-Agent to every request
        builder.addInterceptor(userAgentInterceptor)

        // offer Brotli and gzip compression (can be disabled per request with `Accept-Encoding: identity`)
        builder.addInterceptor(BrotliInterceptor)

        // app-wide custom proxy support
        buildProxy(builder)

        // add connection security (including client certificates) and authentication
        buildConnectionSecurity(builder)
        buildAuthentication(builder)

        // add network logging, if requested
        if (logger.isLoggable(Level.FINEST)) {
            val loggingInterceptor = HttpLoggingInterceptor { message -> logger.finest(message) }
            loggingInterceptor.redactHeader(OkHttpHeaders.AUTHORIZATION)
            loggingInterceptor.redactHeader(OkHttpHeaders.COOKIE)
            loggingInterceptor.redactHeader(OkHttpHeaders.SET_COOKIE)
            loggingInterceptor.redactHeader(OkHttpHeaders.SET_COOKIE2)
            loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            builder.addNetworkInterceptor(loggingInterceptor)
        }
    }

    private fun buildAuthentication(okBuilder: OkHttpClient.Builder) {
        // basic/digest auth and OAuth
        authenticationInterceptor?.let { okBuilder.addInterceptor(it) }
        authenticator?.let { okBuilder.authenticator(it) }
    }

    private fun buildConnectionSecurity(okBuilder: OkHttpClient.Builder) {
        // Allow cleartext and TLS 1.2+
        okBuilder.connectionSpecs(listOf(
            ConnectionSpec.CLEARTEXT,
            ConnectionSpec.MODERN_TLS
        ))

        /* Set SSLSocketFactory, TrustManager and HostnameVerifier (if needed).
         * We shouldn't create these things here, because
         *
         * a. it involves complex logic that should be the responsibility of a dedicated class, and
         * b. we need to cache the instances because otherwise, HTTPS connection are not used
         *    correctly. okhttp checks the SSLSocketFactory/TrustManager of a connection in the pool
         *    and creates a new connection when they have changed. */
        val securityContext = connectionSecurityManager.getContext(certificateAlias)

        if (securityContext.disableHttp2)
            okBuilder.protocols(listOf(Protocol.HTTP_1_1))

        if (securityContext.sslSocketFactory != null && securityContext.trustManager != null)
            okBuilder.sslSocketFactory(securityContext.sslSocketFactory, securityContext.trustManager)

        if (securityContext.hostnameVerifier != null)
            okBuilder.hostnameVerifier(securityContext.hostnameVerifier)
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


    // Ktor builder

    /**
     * Builds a Ktor [HttpClient] with the configured settings.
     *
     * [buildKtor] or [build] must be called only once because multiple calls indicate this wrong usage pattern:
     *
     * ```
     * val builder = HttpClientBuilder(/*injected*/)
     * val client1 = builder.configure().buildKtor()
     * val client2 = builder.configureOtherwise().buildKtor()
     * ```
     *
     * However in this case the configuration of `client1` is still in `builder` and would be reused for `client2`,
     * which is usually not desired.
     *
     * @return the new HttpClient (with [OkHttp] engine) which **must be closed by the caller**
     */
    @MustBeClosed
    fun buildKtor(): HttpClient {
        if (alreadyBuilt)
            logger.warning("buildKtor() should only be called once; use Provider<HttpClientBuilder> instead")

        val client = HttpClient(OkHttp) {
            // Ktor-level configuration here

            // don't follow redirects by default because it would break PROPFIND handling;
            // this controls whether Ktor's HttpRedirect plugin is active
            followRedirects = this@HttpClientBuilder.followRedirects

            // Uses AcceptAllCookiesStorage, which stores all the cookies in an in-memory map.
            install(HttpCookies)

            // automatically convert JSON from/into data classes (if requested in respective code)
            install(ContentNegotiation) {
                // use lenient parser that ignores unknown keys
                json(lenientJson)
            }

            // Set User-Agent and Accept-Language on every request (locale is read per request)
            install(DefaultRequest) {
                val userAgent = productIds.httpUserAgent
                logger.info("Will use User-Agent: $userAgent")

                val locale = Locale.getDefault()
                headers.appendIfNameAbsent(HttpHeaders.UserAgent, userAgent)
                headers.appendIfNameAbsent(
                    HttpHeaders.AcceptLanguage,
                    "${locale.language}-${locale.country}, ${locale.language};q=0.7, *;q=0.5"
                )
            }

            // offer gzip/deflate compression and decompress responses transparently
            install(ContentEncoding) {
                gzip()
                deflate()
            }

            // add network logging (with redaction of sensitive headers), if requested
            if (logger.isLoggable(Level.FINEST)) {
                install(Logging) {
                    logger = object : io.ktor.client.plugins.logging.Logger {
                        override fun log(message: String) {
                            this@HttpClientBuilder.logger.finest(message)
                        }
                    }
                    level = loggerInterceptorLevel
                    sanitizeHeader { header ->
                        header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                                header.equals(HttpHeaders.Cookie, ignoreCase = true) ||
                                header.equals(HttpHeaders.SetCookie, ignoreCase = true) ||
                                header.equals(
                                    "Set-Cookie2",
                                    ignoreCase = true
                                ) // Obsoleted, but included here for good measure
                    }
                }
            }

            engine {
                // okhttp engine configuration here

                config {
                    // OkHttpClient.Builder configuration here

                    // we don't use the sharedOkHttpClient, so we have to apply timeouts again
                    configureTimeouts(this)

                    // build most config on okhttp level
                    configureOkHttp(this)
                }
            }
        }

        alreadyBuilt = true
        return client
    }

}