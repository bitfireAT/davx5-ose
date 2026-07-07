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
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
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
import javax.annotation.CheckReturnValue
import javax.inject.Inject

/**
 * Immutable/chainable builder for the HTTP client.
 *
 * Every configuration method (`logTo`, `authenticate`, `followRedirects`, ...) returns
 * a new instance with the change applied.
 */
class HttpClientBuilder private constructor(
    // below are coming from Hilt
    private val accountSettingsFactory: AccountSettings.Factory,
    private val connectionSecurityManager: ConnectionSecurityManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val oAuthProviderFactory: OAuthProvider.Factory,
    private val productIds: ProductIds,
    private val settingsManager: SettingsManager,
    // except the current configuration of this builder instance (immutable)
    private val config: Config
) {

    // public constructor, delegating to private constructor with empty Config()
    @Inject
    constructor(
        accountSettingsFactory: AccountSettings.Factory,
        connectionSecurityManager: ConnectionSecurityManager,
        defaultLogger: Logger,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        oAuthProviderFactory: OAuthProvider.Factory,
        productIds: ProductIds,
        settingsManager: SettingsManager
    ) : this(
        accountSettingsFactory = accountSettingsFactory,
        connectionSecurityManager = connectionSecurityManager,
        ioDispatcher = ioDispatcher,
        oAuthProviderFactory = oAuthProviderFactory,
        productIds = productIds,
        settingsManager = settingsManager,
        config = Config(logger = defaultLogger)
    )


    // methods to set configuration

    /**
     * Immutable snapshot of everything that can be configured on a [HttpClientBuilder] plus default config.
     */
    private data class Config(
        val followRedirects: Boolean = false,
        val logger: Logger,
        val trafficLogLevel: LogLevel = LogLevel.ALL,
        val certificateAlias: String? = null,
        val authUsername: String? = null,
        val authPassword: SensitiveString? = null,
        val authDomain: String? = null,
        val readAuthStateCallback: (() -> AuthState?)? = null,
        val updateAuthStateCallback: ((AuthState) -> Unit)? = null
    )

    /**
     * Creates a new [HttpClientBuilder] with updated configuration.
     *
     * @param update block that takes the current Config and returns a new Config with desired changes
     * @return new instance with updated config
     */
    private fun withConfig(update: (Config) -> Config) = HttpClientBuilder(
        accountSettingsFactory,
        connectionSecurityManager,
        ioDispatcher,
        oAuthProviderFactory,
        productIds,
        settingsManager,
        update(config)
    )

    /**
     * Sets whether the HTTP client should automatically follow redirects.
     *
     * @param follow true to follow redirects, false otherwise
     * @return new builder with updated config (chainable)
     */
    @CheckReturnValue
    fun followRedirects(follow: Boolean): HttpClientBuilder = withConfig { it.copy(followRedirects = follow) }

    /**
     * Sets the logger for the HTTP client (mainly used to log HTTP traffic).
     *
     * @param logger The logger to be used for logging HTTP operations.
     * @return new builder with updated config (chainable)
     */
    @CheckReturnValue
    fun logTo(logger: Logger): HttpClientBuilder = withConfig { it.copy(logger = logger) }

    /**
     * Sets the log level for HTTP traffic logging.
     *
     * @param level The desired log level for traffic logs.
     * @return new builder with updated config (chainable)
     */
    @CheckReturnValue
    fun trafficLogLevel(level: LogLevel): HttpClientBuilder = withConfig { it.copy(trafficLogLevel = level) }

    /**
     * Configures authentication for the HTTP client.
     *
     * @param domain Domain for which the credentials are valid. If null, credentials are sent for all domains.
     * @param getCredentials Provider function that returns the credentials to use.
     * @param updateAuthState Optional callback to update the OAuth auth-state.
     * @return new builder with updated config (chainable)
     */
    @CheckReturnValue
    fun authenticate(
        domain: String?,
        getCredentials: () -> Credentials,
        updateAuthState: ((AuthState) -> Unit)? = null
    ): HttpClientBuilder = withConfig { config ->
        val credentials = getCredentials()
        var newConfig = config
        when {
            // OAuth
            credentials.authState != null -> {
                newConfig = newConfig.copy(
                    authUsername = null,
                    authPassword = null,
                    authDomain = domain,
                    readAuthStateCallback = {
                        // We don't use the "credentials" object from above because it may contain an outdated
                        // access token. Instead, we fetch the up-to-date auth-state on each readAuthState call.
                        getCredentials().authState
                    },
                    updateAuthStateCallback = updateAuthState
                )
            }

            // basic / digest auth
            credentials.username != null && credentials.password != null -> {
                newConfig = newConfig.copy(
                    authUsername = credentials.username,
                    authPassword = credentials.password,
                    authDomain = domain,
                    readAuthStateCallback = null,
                    updateAuthStateCallback = null
                )
            }
        }

        // client certificate
        if (credentials.certificateAlias != null)
            newConfig = newConfig.copy(certificateAlias = credentials.certificateAlias)

        return@withConfig newConfig
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
        return authenticate(
            domain = UrlUtils.hostToDomain(authDomain),
            getCredentials = {
                accountSettings.credentials()
            },
            updateAuthState = { authState ->
                accountSettings.updateAuthState(authState)
            }
        )
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


    // actual client building

    private fun buildConnectionSecurity(okBuilder: OkHttpClient.Builder) {
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
        val securityContext = connectionSecurityManager.getContext(config.certificateAlias)

        if (securityContext.disableHttp2)
            okBuilder.protocols(listOf(Protocol.HTTP_1_1))

        if (securityContext.sslSocketFactory != null && securityContext.trustManager != null)
            okBuilder.sslSocketFactory(securityContext.sslSocketFactory, securityContext.trustManager)

        if (securityContext.hostnameVerifier != null)
            okBuilder.hostnameVerifier(securityContext.hostnameVerifier)
    }

    private fun buildProxy(engineConfig: HttpClientEngineConfig) {
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
            config.logger.info("Using non-default proxy setting: $proxy")
            engineConfig.proxy = proxy
        }
    }

    private fun HttpClientConfig<*>.installAuthPlugin() {
        val username = config.authUsername
        val password = config.authPassword
        val readAuthState = config.readAuthStateCallback
        val updateAuthState = config.updateAuthStateCallback
        when {
            // prefer OAuth, if available
            readAuthState != null -> {
                install(Auth) {
                    providers.add(
                        oAuthProviderFactory.create(
                            readAuthState = readAuthState,
                            writeAuthState = { authState -> updateAuthState?.invoke(authState) }
                        ).authProvider(config.authDomain)
                    )
                }
            }

            // otherwise use basic / digest, if available
            username != null && password != null -> {
                install(Auth) {
                    providers.add(
                        createDomainBasicAuthProvider(
                            username = username,
                            password = password.asString(),
                            firstLevelDomain = config.authDomain,
                            insecurePreemptive = true
                        )
                    )
                    providers.add(
                        createDomainDigestAuthProvider(
                            username = username,
                            password = password.asString(),
                            firstLevelDomain = config.authDomain
                        )
                    )
                }
            }
        }
    }

    /**
     * Installs all Ktor-level plugins (cookies, timeouts, content negotiation, user agent,
     * compression, auth, logging) that are shared between [build] (real [OkHttp] engine) and
     * [build] (arbitrary engine, used for tests).
     */
    private fun HttpClientConfig<*>.installPlugins() {
        // don't follow redirects by default because it would break PROPFIND handling;
        // this controls whether Ktor's HttpRedirect plugin is active
        followRedirects = config.followRedirects

        installAuthPlugin()

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
        if (config.logger.isLoggable(Level.FINEST)) {
            install(Logging) {
                logger = object : io.ktor.client.plugins.logging.Logger {
                    override fun log(message: String) {
                        config.logger.finest(message)
                    }
                }
                level = config.trafficLogLevel

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
    }

    /**
     * Builds a Ktor [HttpClient] with the configured settings.
     *
     * Since [HttpClientBuilder] is immutable, this can be called any number of times — on this
     * builder or on any builder derived from it via the configuration methods — and each call
     * produces an independent [HttpClient] that only reflects the configuration of the builder
     * it was called on.
     *
     * @return the new HttpClient (with [OkHttp] engine) which **must be closed by the caller**
     */
    @MustBeClosed
    fun build(): HttpClient {
        val client = HttpClient(OkHttp) {
            installPlugins()

            engine {
                // app-wide custom proxy support
                buildProxy(this)

                // okhttp engine configuration here
                config {
                    // OkHttpClient.Builder configuration here

                    // TLS settings
                    buildConnectionSecurity(this)
                }
            }
        }

        return client
    }

    /**
     * Same as [build] but uses the provided [engine] instead of [OkHttp]. Intended for tests so
     * that a `MockEngine` can be injected while all Ktor-level plugins (cookies, logging, default
     * request headers, …) are still applied.
     *
     * @return the new HttpClient (with the provided [engine]) which **must be closed by the caller**
     */
    @MustBeClosed
    internal fun <CE : HttpClientEngine> build(engine: CE): HttpClient =
        HttpClient(engine) {
            installPlugins()
        }

}
