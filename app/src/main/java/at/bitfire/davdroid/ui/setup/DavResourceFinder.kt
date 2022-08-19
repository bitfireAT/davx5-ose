/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/
package at.bitfire.davdroid.ui.setup

import android.content.Context
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.exception.UnauthorizedException
import at.bitfire.dav4jvm.property.*
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.log.StringHandler
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.xbill.DNS.Lookup
import org.xbill.DNS.Type
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class DavResourceFinder(
        val context: Context,
        private val loginModel: LoginModel
): AutoCloseable {

    enum class Service(val wellKnownName: String) {
        CALDAV("caldav"),
        CARDDAV("carddav");

        override fun toString() = wellKnownName
    }

    val log: Logger = Logger.getLogger("davx5.DavResourceFinder")
    private val logBuffer = StringHandler()
    init {
        log.level = Level.FINEST
        log.addHandler(logBuffer)
    }

    var encountered401 = false

    private val httpClient: HttpClient = HttpClient.Builder(context, logger = log).let {
        loginModel.credentials?.let { credentials ->
            it.addAuthentication(null, credentials)
        }
        it.setForeground(true)
        it.build()
    }

    override fun close() {
        httpClient.close()
    }


    /**
     * Finds the initial configuration, i.e. runs the auto-detection process. Must not throw an
     * exception, but return an empty Configuration with error logs instead.
     */
    fun findInitialConfiguration(): Configuration {
        var cardDavConfig: Configuration.ServiceInfo? = null
        var calDavConfig: Configuration.ServiceInfo? = null

        try {
            try {
                cardDavConfig = findInitialConfiguration(Service.CARDDAV)
            } catch (e: Exception) {
                log.log(Level.INFO, "CardDAV service detection failed", e)
                processException(e)
            }

            try {
                calDavConfig = findInitialConfiguration(Service.CALDAV)
            } catch (e: Exception) {
                log.log(Level.INFO, "CalDAV service detection failed", e)
                processException(e)
            }
        } catch(e: Exception) {
            // we have been interrupted; reset results so that an error message will be shown
            cardDavConfig = null
            calDavConfig = null
        }

        return Configuration(
                cardDavConfig, calDavConfig,
                encountered401,
                logBuffer.toString()
        )
    }

    private fun findInitialConfiguration(service: Service): Configuration.ServiceInfo? {
        // user-given base URI (either mailto: URI or http(s):// URL)
        val baseURI = loginModel.baseURI!!

        // domain for service discovery
        var discoveryFQDN: String? = null

        // put discovered information here
        val config = Configuration.ServiceInfo()
        log.info("Finding initial ${service.wellKnownName} service configuration")

        if (baseURI.scheme.equals("http", true) || baseURI.scheme.equals("https", true)) {
            baseURI.toHttpUrlOrNull()?.let { baseURL ->
                // remember domain for service discovery
                // try service discovery only for https:// URLs because only secure service discovery is implemented
                if (baseURL.scheme.equals("https", true))
                    discoveryFQDN = baseURL.host

                checkUserGivenURL(baseURL, service, config)

                if (config.principal == null)
                    try {
                        config.principal = getCurrentUserPrincipal(baseURL.resolve("/.well-known/" + service.wellKnownName)!!, service)
                    } catch(e: Exception) {
                        log.log(Level.FINE, "Well-known URL detection failed", e)
                        processException(e)
                    }
            }
        } else if (baseURI.scheme.equals("mailto", true)) {
            val mailbox = baseURI.schemeSpecificPart

            val posAt = mailbox.lastIndexOf("@")
            if (posAt != -1)
                discoveryFQDN = mailbox.substring(posAt + 1)
        }

        // Step 2: If user-given URL didn't reveal a principal, search for it: SERVICE DISCOVERY
        if (config.principal == null)
            discoveryFQDN?.let {
                log.info("No principal found at user-given URL, trying to discover")
                try {
                    config.principal = discoverPrincipalUrl(it, service)
                } catch(e: Exception) {
                    log.log(Level.FINE, "$service service discovery failed", e)
                    processException(e)
                }
            }

        // detect email address
        if (service == Service.CALDAV)
            config.principal?.let {
                config.emails.addAll(queryEmailAddress(it))
            }

        // return config or null if config doesn't contain useful information
        val serviceAvailable = config.principal != null || config.homeSets.isNotEmpty() || config.collections.isNotEmpty()
        return if (serviceAvailable)
            config
        else
            null
    }

    private fun checkUserGivenURL(baseURL: HttpUrl, service: Service, config: Configuration.ServiceInfo) {
        log.info("Checking user-given URL: $baseURL")

        val davBase = DavResource(httpClient.okHttpClient, baseURL, log)
        try {
            when (service) {
                Service.CARDDAV -> {
                    davBase.propfind(0,
                            ResourceType.NAME, DisplayName.NAME, AddressbookDescription.NAME,
                            AddressbookHomeSet.NAME,
                            CurrentUserPrincipal.NAME
                    ) { response, _ ->
                        scanCardDavResponse(response, config)
                    }
                }
                Service.CALDAV -> {
                    davBase.propfind(0,
                            ResourceType.NAME, DisplayName.NAME, CalendarColor.NAME, CalendarDescription.NAME, CalendarTimezone.NAME, CurrentUserPrivilegeSet.NAME, SupportedCalendarComponentSet.NAME,
                            CalendarHomeSet.NAME,
                            CurrentUserPrincipal.NAME
                    ) { response, _ ->
                        scanCalDavResponse(response, config)
                    }
                }
            }
        } catch(e: Exception) {
            log.log(Level.FINE, "PROPFIND/OPTIONS on user-given URL failed", e)
            processException(e)
        }
    }

    /**
     * Queries a user's email address using CalDAV scheduling: calendar-user-address-set.
     * @param principal principal URL of the user
     * @return list of found email addresses (empty if none)
     */
    fun queryEmailAddress(principal: HttpUrl): List<String> {
        val mailboxes = LinkedList<String>()
        try {
            DavResource(httpClient.okHttpClient, principal, log).propfind(0, CalendarUserAddressSet.NAME) { response, _ ->
                response[CalendarUserAddressSet::class.java]?.let { addressSet ->
                    for (href in addressSet.hrefs)
                        try {
                            val uri = URI(href)
                            if (uri.scheme.equals("mailto", true))
                                mailboxes.add(uri.schemeSpecificPart)
                        } catch(e: URISyntaxException) {
                            log.log(Level.WARNING, "Couldn't parse user address", e)
                        }
                }
            }
        } catch(e: Exception) {
            log.log(Level.WARNING, "Couldn't query user email address", e)
            processException(e)
        }
        return mailboxes
    }

    /**
     * If [dav] references an address book, an address book home set, and/or a princiapl,
     * it will added to, config.collections, config.homesets and/or config.principal.
     * URLs will be stored with trailing "/".
     *
     * @param dav       response whose properties are evaluated
     * @param config    structure where the results are stored into
     */
    fun scanCardDavResponse(dav: Response, config: Configuration.ServiceInfo) {
        var principal: HttpUrl? = null

        // check for current-user-principal
        dav[CurrentUserPrincipal::class.java]?.href?.let {
            principal = dav.requestedUrl.resolve(it)
        }

        // Is it an address book and/or principal?
        dav[ResourceType::class.java]?.let {
            if (it.types.contains(ResourceType.ADDRESSBOOK)) {
                val info = Collection.fromDavResponse(dav)!!
                log.info("Found address book at ${info.url}")
                config.collections[info.url] = info
            }

            if (it.types.contains(ResourceType.PRINCIPAL))
                principal = dav.href
        }

        // Is it an addressbook-home-set?
        dav[AddressbookHomeSet::class.java]?.let { homeSet ->
            for (href in homeSet.hrefs) {
                dav.requestedUrl.resolve(href)?.let {
                    val location = UrlUtils.withTrailingSlash(it)
                    log.info("Found address book home-set at $location")
                    config.homeSets += location
                }
            }
        }

        principal?.let {
            if (providesService(it, Service.CARDDAV))
                config.principal = principal
        }
    }

    /**
     * If [dav] references an address book, an address book home set, and/or a princiapl,
     * it will added to, config.collections, config.homesets and/or config.principal.
     * URLs will be stored with trailing "/".
     *
     * @param dav       response whose properties are evaluated
     * @param config    structure where the results are stored into
     */
    private fun scanCalDavResponse(dav: Response, config: Configuration.ServiceInfo) {
        var principal: HttpUrl? = null

        // check for current-user-principal
        dav[CurrentUserPrincipal::class.java]?.href?.let {
            principal = dav.requestedUrl.resolve(it)
        }

        // Is it a calendar and/or principal?
        dav[ResourceType::class.java]?.let {
            if (it.types.contains(ResourceType.CALENDAR)) {
                val info = Collection.fromDavResponse(dav)!!
                log.info("Found calendar at ${info.url}")
                config.collections[info.url] = info
            }

            if (it.types.contains(ResourceType.PRINCIPAL))
                principal = dav.href
        }

        // Is it an calendar-home-set?
        dav[CalendarHomeSet::class.java]?.let { homeSet ->
            for (href in homeSet.hrefs) {
                dav.requestedUrl.resolve(href)?.let {
                    val location = UrlUtils.withTrailingSlash(it)
                    log.info("Found calendar home-set at $location")
                    config.homeSets += location
                }
            }
        }

        principal?.let {
            if (providesService(it, Service.CALDAV))
                config.principal = principal
        }
    }


    @Throws(IOException::class)
    fun providesService(url: HttpUrl, service: Service): Boolean {
        var provided = false
        try {
            DavResource(httpClient.okHttpClient, url, log).options { capabilities, _ ->
                if ((service == Service.CARDDAV && capabilities.contains("addressbook")) ||
                    (service == Service.CALDAV && capabilities.contains("calendar-access")))
                    provided = true
            }
        } catch(e: Exception) {
            log.log(Level.SEVERE, "Couldn't detect services on $url", e)
            if (e !is HttpException && e !is DavException)
                throw e
        }
        return provided
    }


    /**
     * Try to find the principal URL by performing service discovery on a given domain name.
     * Only secure services (caldavs, carddavs) will be discovered!
     * @param domain         domain name, e.g. "icloud.com"
     * @param service        service to discover (CALDAV or CARDDAV)
     * @return principal URL, or null if none found
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun discoverPrincipalUrl(domain: String, service: Service): HttpUrl? {
        val scheme: String
        val fqdn: String
        var port = 443
        val paths = LinkedList<String>()     // there may be multiple paths to try

        val query = "_${service.wellKnownName}s._tcp.$domain"
        log.fine("Looking up SRV records for $query")

        val srvLookup = Lookup(query, Type.SRV)
        DavUtils.prepareLookup(context, srvLookup)
        val srv = DavUtils.selectSRVRecord(srvLookup.run().orEmpty())

        if (srv != null) {
            // choose SRV record to use (query may return multiple SRV records)
            scheme = "https"
            fqdn = srv.target.toString(true)
            port = srv.port
            log.info("Found $service service at https://$fqdn:$port")
        } else {
            // no SRV records, try domain name as FQDN
            log.info("Didn't find $service service, trying at https://$domain:$port")

            scheme = "https"
            fqdn = domain
        }

        // look for TXT record too (for initial context path)
        val txtLookup = Lookup(query, Type.TXT)
        DavUtils.prepareLookup(context, txtLookup)
        paths.addAll(DavUtils.pathsFromTXTRecords(txtLookup.run()))

        // if there's TXT record and if it it's wrong, try well-known
        paths.add("/.well-known/" + service.wellKnownName)
        // if this fails, too, try "/"
        paths.add("/")

        for (path in paths)
            try {
                val initialContextPath = HttpUrl.Builder()
                        .scheme(scheme)
                        .host(fqdn).port(port)
                        .encodedPath(path)
                        .build()

                log.info("Trying to determine principal from initial context path=$initialContextPath")
                val principal = getCurrentUserPrincipal(initialContextPath, service)

                principal?.let { return it }
            } catch(e: Exception) {
                log.log(Level.WARNING, "No resource found", e)
                processException(e)
            }
        return null
    }

    /**
     * Queries a given URL for current-user-principal
     *
     * @param url       URL to query with PROPFIND (Depth: 0)
     * @param service   required service (may be null, in which case no service check is done)
     * @return          current-user-principal URL that provides required service, or null if none
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun getCurrentUserPrincipal(url: HttpUrl, service: Service?): HttpUrl? {
        var principal: HttpUrl? = null
        DavResource(httpClient.okHttpClient, url, log).propfind(0, CurrentUserPrincipal.NAME) { response, _ ->
            response[CurrentUserPrincipal::class.java]?.href?.let { href ->
                response.requestedUrl.resolve(href)?.let {
                    log.info("Found current-user-principal: $it")

                    // service check
                    if (service != null && !providesService(it, service))
                        log.info("$it doesn't provide required $service service")
                    else
                        principal = it
                }
            }
        }
        return principal
    }

    /**
     * Processes a thrown exception likes this:
     *
     *   - If the Exception is an [UnauthorizedException] (HTTP 401), [encountered401] is set to *true*.
     *   - Re-throws the exception if it signals that the current thread was interrupted to stop the current operation.
     */
    private fun processException(e: Exception) {
        if (e is UnauthorizedException)
            encountered401 = true
        else if ((e is InterruptedIOException && e !is SocketTimeoutException) || e is InterruptedException)
            throw e
    }


    // data classes

    class Configuration(
            val cardDAV: ServiceInfo?,
            val calDAV: ServiceInfo?,

            val encountered401: Boolean,
            val logs: String
    ) {

        data class ServiceInfo(
                var principal: HttpUrl? = null,
                val homeSets: MutableSet<HttpUrl> = HashSet(),
                val collections: MutableMap<HttpUrl, Collection> = HashMap(),

                val emails: MutableList<String> = LinkedList()
        )

        override fun toString(): String {
            val builder = ReflectionToStringBuilder(this)
            builder.setExcludeFieldNames("logs")
            return builder.toString()
        }

    }

}
