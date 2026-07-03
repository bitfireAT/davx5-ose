/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.accounts.Account
import androidx.annotation.WorkerThread
import at.bitfire.dav4jvm.ktor.UrlUtils
import at.bitfire.dav4jvm.ktor.createDomainBasicAuthProvider
import at.bitfire.dav4jvm.ktor.createDomainDigestAuthProvider
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.synctools.util.SensitiveString
import com.google.errorprone.annotations.MustBeClosed
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.Proxy
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

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
    private val oAuthProviderFactory: OAuthProvider.Factory,
    private val settingsManager: SettingsManager,
    private val productIds: ProductIds
) {

    companion object {

        init {
            // make sure Conscrypt is available when the HttpClientBuilder class is loaded the first time
            ConscryptIntegration().initialize()
        }

    }

    /**
     * Flag to prevent multiple [buildKtor] calls
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


    private var certificateAlias: String? = null

    private var authUsername: String? = null
    private var authPassword: SensitiveString? = null
    private var authDomain: String? = null
    private var oAuthProvider: BearerAuthProvider? = null

    fun authenticate(
        domain: String?,
        getCredentials: () -> Credentials,
        updateAuthState: ((AuthState) -> Unit)? = null
    ): HttpClientBuilder {
        val credentials = getCredentials()
        when {
            // OAuth
            credentials.authState != null -> {
                oAuthProvider = oAuthProviderFactory.create(
                    readAuthState = {
                        /* We don't use the "credentials" object from above because it may contain an outdated
                        access token. Instead, we fetch the up-to-date auth-state on each readAuthState call. */
                        getCredentials().authState
                    },
                    writeAuthState = { authState ->
                        updateAuthState?.invoke(authState)
                    }
                ).authProvider()
            }

            // basic / digest auth
            credentials.username != null && credentials.password != null -> {
                authUsername = credentials.username
                authPassword = credentials.password
                authDomain = domain
            }
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
    suspend fun fromAccountAsync(account: Account, onlyHost: String? = null): HttpClientBuilder =
        withContext(ioDispatcher) {
            fromAccount(account, onlyHost)
        }


    // client configuration

    private fun configureConnectionSecurity(okBuilder: OkHttpClient.Builder) {
        // Allow cleartext and TLS 1.2+
        okBuilder.connectionSpecs(
            listOf(
                ConnectionSpec.CLEARTEXT,
                ConnectionSpec.MODERN_TLS
            )
        )

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

    private fun configureProxy(engineConfig: HttpClientEngineConfig) {
        val proxy = when (settingsManager.getInt(Settings.PROXY_TYPE)) {
            Settings.PROXY_TYPE_SYSTEM -> null
            Settings.PROXY_TYPE_NONE -> Proxy.NO_PROXY
            Settings.PROXY_TYPE_HTTP -> ProxyBuilder.http(
                URLBuilder(
                    protocol = URLProtocol.HTTP,
                    host = settingsManager.getString(Settings.PROXY_HOST) ?: "",
                    port = settingsManager.getInt(Settings.PROXY_PORT)
                ).build()
            )
            Settings.PROXY_TYPE_SOCKS -> ProxyBuilder.socks(
                settingsManager.getString(Settings.PROXY_HOST) ?: "",
                settingsManager.getInt(Settings.PROXY_PORT)
            )
            else -> /* Invalid proxy type, shouldn't happen */ null
        }
        if (proxy != null) {
            logger.log(Level.INFO, "Using non-default proxy setting", proxy)
            engineConfig.proxy = proxy
        }
    }


    // client builder

    /**
     * Builds a Ktor [HttpClient] with the configured settings.
     *
     * [buildKtor] must be called only once because multiple calls indicate this wrong usage pattern:
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

            val username = authUsername
            val password = authPassword
            if ((username != null && password != null) || oAuthProvider != null) {
                install(Auth) {
                    if (username != null && password != null) {
                        providers.add(
                            createDomainBasicAuthProvider(
                                username = username,
                                password = password.asString(),
                                firstLevelDomain = authDomain,
                                insecurePreemptive = true
                            )
                        )
                        providers.add(
                            createDomainDigestAuthProvider(
                                username = username,
                                password = password.asString(),
                                firstLevelDomain = authDomain
                            )
                        )
                    }
                    oAuthProvider?.let { providers.add(it) }
                }
            }

            // Uses AcceptAllCookiesStorage, which stores all the cookies in an in-memory map.
            install(HttpCookies)

            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 120_000       // covers both read and write inactivity
            }

            // automatically convert JSON from/into data classes (if requested in respective code)
            install(ContentNegotiation) {
                // use lenient parser that ignores unknown keys
                json(lenientJson)
            }

            // Set User-Agent and Accept-Language on every request
            install(UserAgent) {
                agent = productIds.httpUserAgent
            }
            install(DefaultRequest) {
                val locale = Locale.getDefault()
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

                    // don't log some confidential headers
                    val headersToIgnore = arrayOf(
                        HttpHeaders.Authorization,
                        HttpHeaders.Cookie,
                        HttpHeaders.SetCookie,
                        "Set-Cookie2"       // obsoleted, but included here for good measure
                    )
                    sanitizeHeader { header ->
                        headersToIgnore.any { headerToIgnore ->
                            header.equals(headerToIgnore, ignoreCase = true)
                        }
                    }
                }
            }

            engine {
                // app-wide custom proxy support
                configureProxy(this)

                // okhttp engine configuration here
                config {
                    // OkHttpClient.Builder configuration here

                    // TLS settings
                    configureConnectionSecurity(this)
                }
            }
        }

        alreadyBuilt = true
        return client
    }

}