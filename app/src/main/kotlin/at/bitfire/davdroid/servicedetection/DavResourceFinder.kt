/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/
package at.bitfire.davdroid.servicedetection

import android.content.Context
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.exception.UnauthorizedException
import at.bitfire.dav4jvm.property.*
import at.bitfire.dav4jvm.property.caldav.CalendarColor
import at.bitfire.dav4jvm.property.caldav.CalendarDescription
import at.bitfire.dav4jvm.property.caldav.CalendarHomeSet
import at.bitfire.dav4jvm.property.caldav.CalendarTimezone
import at.bitfire.dav4jvm.property.caldav.CalendarUserAddressSet
import at.bitfire.dav4jvm.property.caldav.SupportedCalendarComponentSet
import at.bitfire.dav4jvm.property.carddav.AddressbookDescription
import at.bitfire.dav4jvm.property.carddav.AddressbookHomeSet
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrincipal
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.HrefListProperty
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.StringHandler
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.util.DavUtils
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.xbill.DNS.Lookup
import org.xbill.DNS.Type
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Does initial resource detection (straight after app install). Called after user has supplied url in
 * app setup process [at.bitfire.davdroid.ui.setup.DetectConfigurationFragment].
 * It uses the (user given) base URL to find
 * - services (CalDAV and/or CardDAV),
 * - principal,
 * - homeset/collections (multistatus responses are handled through dav4jvm).
 *
 * @param context        to build the HTTP client
 * @param baseURI        user-given base URI (either mailto: URI or http(s):// URL)
 * @param credentials    optional login credentials (username/password, client certificate, OAuth state)
 */
class DavResourceFinder(
    val context: Context,
    private val baseURI: URI,
    private val credentials: Credentials? = null
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
        credentials?.let { credentials ->
            it.addAuthentication(null, credentials)
        }
        it.setForeground(true)
        it.build()
    }

    override fun close() {
        httpClient.close()
    }


    /**
     * Finds the initial configuration (= runs the service detection process).
     *
     * In case of an error, it returns an empty [Configuration] with error logs
     * instead of throwing an [Exception].
     *
     * @return service information – if there's neither a CalDAV service nor a CardDAV service,
     * service detection was not successful
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
            cardDAV = cardDavConfig,
            calDAV = calDavConfig,
            encountered401 = encountered401,
            logs = logBuffer.toString()
        )
    }

    private fun findInitialConfiguration(service: Service): Configuration.ServiceInfo? {
        // domain for service discovery
        var discoveryFQDN: String? = null

        // discovered information goes into this config
        val config = Configuration.ServiceInfo()

        // Start discovering
        log.info("Finding initial ${service.wellKnownName} service configuration")
        when (baseURI.scheme.lowercase()) {
            "http", "https" ->
                baseURI.toHttpUrlOrNull()?.let { baseURL ->
                    // remember domain for service discovery
                    if (baseURL.scheme.equals("https", true))
                        // service discovery will only be tried for https URLs, because only secure service discovery is implemented
                        discoveryFQDN = baseURL.host

                    // Actual discovery process
                    checkBaseURL(baseURL, service, config)

                    // If principal was not found already, try well known URI
                    if (config.principal == null)
                        try {
                            config.principal = getCurrentUserPrincipal(baseURL.resolve("/.well-known/" + service.wellKnownName)!!, service)
                        } catch(e: Exception) {
                            log.log(Level.FINE, "Well-known URL detection failed", e)
                            processException(e)
                        }
                }
            "mailto" -> {
                val mailbox = baseURI.schemeSpecificPart
                val posAt = mailbox.lastIndexOf("@")
                if (posAt != -1)
                    discoveryFQDN = mailbox.substring(posAt + 1)
            }
        }

        // Second try: If user-given URL didn't reveal a principal, search for it (SERVICE DISCOVERY)
        if (config.principal == null)
            discoveryFQDN?.let { fqdn ->
                log.info("No principal found at user-given URL, trying to discover for domain $fqdn")
                try {
                    config.principal = discoverPrincipalUrl(fqdn, service)
                } catch(e: Exception) {
                    log.log(Level.FINE, "$service service discovery failed", e)
                    processException(e)
                }
            }

        // detect email address
        if (service == Service.CALDAV)
            config.principal?.let { principal ->
                config.emails.addAll(queryEmailAddress(principal))
            }

        // return config or null if config doesn't contain useful information
        val serviceAvailable = config.principal != null || config.homeSets.isNotEmpty() || config.collections.isNotEmpty()
        return if (serviceAvailable)
            config
        else
            null
    }

    /**
     * Entry point of the actual discovery process.
     *
     * Queries the user-given URL (= base URL) to detect whether it contains a current-user-principal
     * or whether it is a homeset or collection.
     *
     * @param baseURL   base URL provided by the user
     * @param service   service to detect configuration for
     * @param config    found configuration will be written to this object
     */
    private fun checkBaseURL(baseURL: HttpUrl, service: Service, config: Configuration.ServiceInfo) {
        log.info("Checking user-given URL: $baseURL")

        val davBaseURL = DavResource(httpClient.okHttpClient, baseURL, log)
        try {
            when (service) {
                Service.CARDDAV -> {
                    davBaseURL.propfind(
                        0,
                        ResourceType.NAME, DisplayName.NAME, AddressbookDescription.NAME,
                        AddressbookHomeSet.NAME,
                        CurrentUserPrincipal.NAME
                    ) { response, _ ->
                        scanResponse(ResourceType.ADDRESSBOOK, response, config)
                    }
                }
                Service.CALDAV -> {
                    davBaseURL.propfind(
                        0,
                        ResourceType.NAME, DisplayName.NAME, CalendarColor.NAME, CalendarDescription.NAME, CalendarTimezone.NAME, CurrentUserPrivilegeSet.NAME, SupportedCalendarComponentSet.NAME,
                        CalendarHomeSet.NAME,
                        CurrentUserPrincipal.NAME
                    ) { response, _ ->
                        scanResponse(ResourceType.CALENDAR, response, config)
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
     * Depending on [resourceType] (CalDAV or CardDAV), this method checks whether [davResponse] references
     * - an address book or calendar (actual resource), and/or
     * - an "address book home set" or a "calendar home set", and/or
     * - whether it's a principal.
     *
     * Respectively, this method will add the response to [config.collections], [config.homesets] and/or [config.principal].
     * Collection URLs will be stored with trailing "/".
     *
     * @param resourceType  type of service to search for in the response
     * @param davResponse   response whose properties are evaluated
     * @param config        structure storing the references
     */
    fun scanResponse(resourceType: Property.Name, davResponse: Response, config: Configuration.ServiceInfo) {
        var principal: HttpUrl? = null

        // Type mapping
        val homeSetClass: Class<out HrefListProperty>
        val serviceType: Service
        when (resourceType) {
            ResourceType.ADDRESSBOOK -> {
                homeSetClass = AddressbookHomeSet::class.java
                serviceType = Service.CARDDAV
            }
            ResourceType.CALENDAR -> {
                homeSetClass = CalendarHomeSet::class.java
                serviceType = Service.CALDAV
            }
            else -> throw IllegalArgumentException()
        }

        // check for current-user-principal
        davResponse[CurrentUserPrincipal::class.java]?.href?.let { currentUserPrincipal ->
            principal = davResponse.requestedUrl.resolve(currentUserPrincipal)
        }

        davResponse[ResourceType::class.java]?.let {
            // Is it a calendar or an address book, ...
            if (it.types.contains(resourceType))
                Collection.fromDavResponse(davResponse)?.let { info ->
                    log.info("Found resource of type $resourceType at ${info.url}")
                    config.collections[info.url] = info
                }

            // ... and/or a principal?
            if (it.types.contains(ResourceType.PRINCIPAL))
                principal = davResponse.href
        }

        // Is it an addressbook-home-set or calendar-home-set?
        davResponse[homeSetClass]?.let { homeSet ->
            for (href in homeSet.hrefs) {
                davResponse.requestedUrl.resolve(href)?.let {
                    val location = UrlUtils.withTrailingSlash(it)
                    log.info("Found home-set of type $resourceType at $location")
                    config.homeSets += location
                }
            }
        }

        // Is there a principal too?
        principal?.let {
            if (providesService(it, serviceType))
                config.principal = principal
            else
                log.warning("Principal $principal doesn't provide $serviceType service")
        }
    }

    /**
     * Sends an OPTIONS request to determine whether a URL provides a given service.
     *
     * @param url      URL to check; often a principal URL
     * @param service  service to check for
     *
     * @return whether the URL provides the given service
     */
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
     *
     * @param domain         domain name, e.g. "icloud.com"
     * @param service        service to discover (CALDAV or CARDDAV)
     * @return principal URL, or null if none found
     */
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

        // in case there's a TXT record, but it's wrong, try well-known
        paths.add("/.well-known/" + service.wellKnownName)
        // if this fails too, try "/"
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
    fun getCurrentUserPrincipal(url: HttpUrl, service: Service?): HttpUrl? {
        var principal: HttpUrl? = null
        DavResource(httpClient.okHttpClient, url, log).propfind(0, CurrentUserPrincipal.NAME) { response, _ ->
            response[CurrentUserPrincipal::class.java]?.href?.let { href ->
                response.requestedUrl.resolve(href)?.let {
                    log.info("Found current-user-principal: $it")

                    // service check
                    if (service != null && !providesService(it, service))
                        log.warning("Principal $it doesn't provide $service service")
                    else
                        principal = it
                }
            }
        }
        return principal
    }

    /**
     * Processes a thrown exception like this:
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
