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
import at.bitfire.dav4android.DavResponse
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

    fun cancel() {
        log.warning("Shutting down resource detection")
        httpClient.okHttpClient.dispatcher().executorService().shutdown()
        httpClient.okHttpClient.connectionPool().evictAll()
    }

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
                }
            }

        if (config.principal != null && service == Service.CALDAV) {
            // query email address (CalDAV scheduling: calendar-user-address-set)
            val davPrincipal = DavResource(httpClient.okHttpClient, config.principal!!, log)
            try {
                davPrincipal.propfind(0, CalendarUserAddressSet.NAME).use { response ->
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
            }
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

        var principal: HttpUrl? = null
        try {
            val davBase = DavResource(httpClient.okHttpClient, baseURL, log)

            var response: DavResponse? = null
            try {
                when (service) {
                    Service.CARDDAV -> {
                        response = davBase.propfind(0,
                                ResourceType.NAME, DisplayName.NAME, AddressbookDescription.NAME,
                                AddressbookHomeSet.NAME,
                                CurrentUserPrincipal.NAME
                        )
                        rememberIfAddressBookOrHomeset(response, config)
                    }
                    Service.CALDAV -> {
                        response = davBase.propfind(0,
                                ResourceType.NAME, DisplayName.NAME, CalendarColor.NAME, CalendarDescription.NAME, CalendarTimezone.NAME, CurrentUserPrivilegeSet.NAME, SupportedCalendarComponentSet.NAME,
                                CalendarHomeSet.NAME,
                                CurrentUserPrincipal.NAME
                        )
                        rememberIfCalendarOrHomeset(response, config)
                    }
                }

                // check for current-user-principal
                response.searchProperty(CurrentUserPrincipal::class.java)?.let { (dav, currentUserPrincipal) ->
                    currentUserPrincipal.href?.let {
                        principal = dav.url.resolve(it)
                    }
                }

                // check for resource type "principal"
                if (principal == null)
                    for ((dav, resourceType) in response.searchProperties(ResourceType::class.java)) {
                        if (resourceType.types.contains(ResourceType.PRINCIPAL)) {
                            principal = dav.url
                            break
                        }
                    }
            } finally {
                response?.close()
            }

            // If a principal has been detected successfully, ensure that it provides the required service.
            principal?.let {
                if (providesService(it, service))
                    config.principal = it
            }
        } catch(e: Exception) {
            log.log(Level.FINE, "PROPFIND/OPTIONS on user-given URL failed", e)
        }
    }

    /**
     * If [dav] references an address book or an address book home set, it will added to
     * config.collections or config.homesets. URLs will be stored with trailing "/".
     * @param dav       resource whose properties are evaluated
     * @param config    structure where the address book (collection) and/or home set is stored into (if found)
     */
    fun rememberIfAddressBookOrHomeset(dav: DavResponse, config: Configuration.ServiceInfo) {
        // Is there an address book?
        for ((addressBook, resourceType) in dav.searchProperties(ResourceType::class.java)) {
            if (resourceType.types.contains(ResourceType.ADDRESSBOOK)) {
                val info = CollectionInfo(addressBook)
                log.info("Found address book at ${info.url}")
                config.collections[info.url] = info
            }
        }

        // Is there an addressbook-home-set?
        for ((dav, homeSet) in dav.searchProperties(AddressbookHomeSet::class.java)) {
            for (href in homeSet.hrefs) {
                dav.url.resolve(href)?.let {
                    val location = UrlUtils.withTrailingSlash(it)
                    log.info("Found address book home-set at $location")
                    config.homeSets += location
                }
            }
        }
    }

    private fun rememberIfCalendarOrHomeset(dav: DavResponse, config: Configuration.ServiceInfo) {
        // Is the collection a calendar collection?
        for ((calendar, resourceType) in dav.searchProperties(ResourceType::class.java)) {
            if (resourceType.types.contains(ResourceType.CALENDAR)) {
                val info = CollectionInfo(calendar)
                log.info("Found calendar at ${info.url}")
                config.collections[info.url] = info
            }
        }

        // Is there an calendar-home-set?
        for ((dav, homeSet) in dav.searchProperties(CalendarHomeSet::class.java)) {
            for (href in homeSet.hrefs) {
                dav.url.resolve(href)?.let {
                    val location = UrlUtils.withTrailingSlash(it)
                    log.info("Found calendar home-set at $location")
                    config.homeSets += location
                }
            }
        }
    }


    @Throws(IOException::class)
    fun providesService(url: HttpUrl, service: Service): Boolean {
        val davPrincipal = DavResource(httpClient.okHttpClient, url, log)
        try {
            davPrincipal.options().use {
                val capabilities = it.capabilities
                if ((service == Service.CARDDAV && capabilities.contains("addressbook")) ||
                    (service == Service.CALDAV && capabilities.contains("calendar-access")))
                    return true
            }
        } catch(e: Exception) {
            log.log(Level.SEVERE, "Couldn't detect services on $url", e)
            if (e !is HttpException && e !is DavException)
                throw e
        }
        return false
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
            }
        return null
    }

    /**
     * Queries a given URL for current-user-principal
     * @param url       URL to query with PROPFIND (Depth: 0)
     * @param service   required service (may be null, in which case no service check is done)
     * @return          current-user-principal URL that provides required service, or null if none
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun getCurrentUserPrincipal(url: HttpUrl, service: Service?): HttpUrl? {
        val dav = DavResource(httpClient.okHttpClient, url, log)
        dav.propfind(0, CurrentUserPrincipal.NAME).use {
            it.searchProperty(CurrentUserPrincipal::class.java)?.let { (dav, currentUserPrincipal) ->
                currentUserPrincipal.href?.let { href ->
                    dav.url.resolve(href)?.let { principal ->
                        log.info("Found current-user-principal: $principal")

                        // service check
                        if (service != null && !providesService(principal, service)) {
                            log.info("$principal doesn't provide required $service service")
                            return null
                        }

                        return principal
                    }
                }
            }
        }
        return null
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

        @Suppress("unused")
        @JvmField
        val CREATOR = object: Parcelable.Creator<Configuration> {

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
