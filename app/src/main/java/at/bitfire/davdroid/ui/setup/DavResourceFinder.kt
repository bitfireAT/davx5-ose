/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.Response
import at.bitfire.dav4android.UrlUtils
import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.exception.HttpException
import at.bitfire.dav4android.property.*
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.log.StringHandler
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.Credentials
import at.bitfire.davdroid.settings.Settings
import okhttp3.HttpUrl
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.xbill.DNS.Lookup
import org.xbill.DNS.Type
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class DavResourceFinder(
        val context: Context,
        private val loginInfo: LoginInfo
): AutoCloseable {

    enum class Service(val wellKnownName: String) {
        CALDAV("caldav"),
        CARDDAV("carddav");

        override fun toString() = wellKnownName
    }

    val log = Logger.getLogger("davdroid.DavResourceFinder")!!
    private val logBuffer = StringHandler()
    init {
        log.level = Level.FINEST
        log.addHandler(logBuffer)
    }

    private val settings = Settings.getInstance(context)
    private val httpClient: HttpClient = HttpClient.Builder(context, settings, logger = log)
            .addAuthentication(null, loginInfo.credentials)
            .setForeground(true)
            .build()

    override fun close() {
        settings?.close()
        httpClient.close()
    }


    fun findInitialConfiguration(): Configuration {
        val cardDavConfig = findInitialConfiguration(Service.CARDDAV)
        val calDavConfig = findInitialConfiguration(Service.CALDAV)

        return Configuration(
                loginInfo.credentials,
                cardDavConfig, calDavConfig,
                logBuffer.toString()
        )
    }

    private fun findInitialConfiguration(service: Service): Configuration.ServiceInfo? {
        // user-given base URI (either mailto: URI or http(s):// URL)
        val baseURI = loginInfo.uri

        // domain for service discovery
        var discoveryFQDN: String? = null

        // put discovered information here
        val config = Configuration.ServiceInfo()
        log.info("Finding initial ${service.wellKnownName} service configuration")

        if (baseURI.scheme.equals("http", true) || baseURI.scheme.equals("https", true)) {
            HttpUrl.get(baseURI)?.let { baseURL ->
                // remember domain for service discovery
                // try service discovery only for https:// URLs because only secure service discovery is implemented
                if (baseURL.scheme().equals("https", true))
                    discoveryFQDN = baseURL.host()

                checkUserGivenURL(baseURL, service, config)

                if (config.principal == null)
                    try {
                        config.principal = getCurrentUserPrincipal(baseURL.resolve("/.well-known/" + service.wellKnownName)!!, service)
                    } catch(e: Exception) {
                        log.log(Level.FINE, "Well-known URL detection failed", e)
                        rethrowIfInterrupted(e)
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
                    rethrowIfInterrupted(e)
                }
            }

        if (config.principal != null && service == Service.CALDAV)
            // query email address (CalDAV scheduling: calendar-user-address-set)
            try {
                DavResource(httpClient.okHttpClient, config.principal!!, log).propfind(0, CalendarUserAddressSet.NAME) { response, _ ->
                    response[CalendarUserAddressSet::class.java]?.let { addressSet ->
                        for (href in addressSet.hrefs)
                            try {
                                val uri = URI(href)
                                if (uri.scheme.equals("mailto", true))
                                    config.email = uri.schemeSpecificPart
                            } catch(e: URISyntaxException) {
                                log.log(Level.WARNING, "Couldn't parse user address", e)
                            }
                    }
                }
            } catch(e: Exception) {
                log.log(Level.WARNING, "Couldn't query user email address", e)
                rethrowIfInterrupted(e)
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
            rethrowIfInterrupted(e)
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
    fun scanCardDavResponse(dav: Response, config: Configuration.ServiceInfo) {
        var principal: HttpUrl? = null

        // check for current-user-principal
        dav[CurrentUserPrincipal::class.java]?.href?.let {
            principal = dav.requestedUrl.resolve(it)
        }

        // Is it an address book and/or principal?
        dav[ResourceType::class.java]?.let {
            if (it.types.contains(ResourceType.ADDRESSBOOK)) {
                val info = CollectionInfo(dav)
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
    fun scanCalDavResponse(dav: Response, config: Configuration.ServiceInfo) {
        var principal: HttpUrl? = null

        // check for current-user-principal
        dav[CurrentUserPrincipal::class.java]?.href?.let {
            principal = dav.requestedUrl.resolve(it)
        }

        // Is it a calendar book and/or principal?
        dav[ResourceType::class.java]?.let {
            if (it.types.contains(ResourceType.CALENDAR)) {
                val info = CollectionInfo(dav)
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
                    log.info("Found calendar book home-set at $location")
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
    private fun discoverPrincipalUrl(domain: String, service: Service): HttpUrl? {
        val scheme: String
        val fqdn: String
        var port = 443
        val paths = LinkedList<String>()     // there may be multiple paths to try

        val query = "_${service.wellKnownName}s._tcp.$domain"
        log.fine("Looking up SRV records for $query")
        val srvLookup = Lookup(query, Type.SRV)
        DavUtils.prepareLookup(context, srvLookup)
        val srv = DavUtils.selectSRVRecord(srvLookup.run())
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
                rethrowIfInterrupted(e)
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

    private fun rethrowIfInterrupted(e: Exception) {
        if (e is InterruptedIOException || e is InterruptedException)
            throw e
    }


    // data classes

    class Configuration(
            val credentials: Credentials,

            val cardDAV: ServiceInfo?,
            val calDAV: ServiceInfo?,

            val logs: String
    ): Parcelable {

        data class ServiceInfo(
                var principal: HttpUrl? = null,
                val homeSets: MutableSet<HttpUrl> = HashSet(),
                val collections: MutableMap<HttpUrl, CollectionInfo> = HashMap(),

                var email: String? = null
        )

        override fun toString(): String {
            val builder = ReflectionToStringBuilder(this)
            builder.setExcludeFieldNames("logs")
            return builder.toString()
        }


        override fun describeContents() = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            fun writeServiceInfo(info: ServiceInfo?) {
                if (info == null)
                    dest.writeByte(0)
                else {
                    dest.writeByte(1)
                    dest.writeString(info.principal?.toString())

                    dest.writeInt(info.homeSets.size)
                    info.homeSets.forEach { dest.writeString(it.toString()) }

                    dest.writeInt(info.collections.size)
                    info.collections.forEach { url, collectionInfo ->
                        dest.writeString(url.toString())
                        dest.writeParcelable(collectionInfo, 0)
                    }

                    dest.writeString(info.email)
                }
            }

            dest.writeSerializable(credentials)
            writeServiceInfo(cardDAV)
            writeServiceInfo(calDAV)
            dest.writeString(logs)
        }


        companion object CREATOR : Parcelable.Creator<Configuration> {

            override fun createFromParcel(source: Parcel): Configuration {
                fun readCollections(): MutableMap<HttpUrl, CollectionInfo> {
                    val size = source.readInt()
                    val map = HashMap<HttpUrl, CollectionInfo>(size)
                    (1..size).forEach {
                        val url = HttpUrl.parse(source.readString())!!
                        map[url] = source.readParcelable(Thread.currentThread().contextClassLoader)
                    }
                    return map
                }

                fun readServiceInfo(): ServiceInfo? {
                    return if (source.readByte() == 0.toByte())
                        null
                    else
                        ServiceInfo(
                                source.readString()?.let { HttpUrl.parse(it) },
                                (1..source.readInt()).map { HttpUrl.parse(source.readString())!! }.toMutableSet(),
                                readCollections()
                        )
                }

                return Configuration(
                        source.readSerializable() as Credentials,
                        readServiceInfo(),
                        readServiceInfo(),
                        source.readString()
                )
            }

            override fun newArray(size: Int) = arrayOfNulls<Configuration>(size)

        }

    }

}
