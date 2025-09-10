/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.caldav.CalendarHomeSet
import at.bitfire.dav4jvm.property.caldav.CalendarProxyReadFor
import at.bitfire.dav4jvm.property.caldav.CalendarProxyWriteFor
import at.bitfire.dav4jvm.property.carddav.AddressbookHomeSet
import at.bitfire.dav4jvm.property.common.HrefListProperty
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.GroupMembership
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavHomeSetRepository
import at.bitfire.davdroid.util.DavUtils.parent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.logging.Level
import java.util.logging.Logger

/**
 * ServiceRefresher is used to discover and save home sets of a given service.
 */
class ServiceRefresher @AssistedInject constructor(
    @Assisted private val service: Service,
    @Assisted private val httpClient: OkHttpClient,
    private val logger: Logger,
    private val homeSetRepository: DavHomeSetRepository
) {

    @AssistedFactory
    interface Factory {
        fun create(service: Service, httpClient: OkHttpClient): ServiceRefresher
    }

    /**
     * Home-set class to use depending on the given service type.
     */
    private val homeSetClass: Class<out HrefListProperty> =
        when (service.type) {
            Service.TYPE_CARDDAV -> AddressbookHomeSet::class.java
            Service.TYPE_CALDAV -> CalendarHomeSet::class.java
            else -> throw IllegalArgumentException()
        }

    /**
     * Home-set properties to ask for in a PROPFIND request to the principal URL,
     * depending on the given service type.
     */
    private val homeSetProperties: Array<Property.Name> =
        arrayOf(                        // generic WebDAV properties
            DisplayName.NAME,
            GroupMembership.NAME,
            ResourceType.NAME
        ) + when (service.type) {       // service-specific CalDAV/CardDAV properties
            Service.TYPE_CARDDAV -> arrayOf(
                AddressbookHomeSet.NAME,
            )

            Service.TYPE_CALDAV -> arrayOf(
                CalendarHomeSet.NAME,
                CalendarProxyReadFor.NAME,
                CalendarProxyWriteFor.NAME
            )

            else -> throw IllegalArgumentException()
        }

    /**
     * Starting at given principal URL, tries to recursively find and save all user relevant home sets.
     *
     * @param principalUrl              URL of principal to query (user-provided principal or current-user-principal)
     * @param level                     Current recursion level (limited to 0, 1 or 2):
     *   - 0: We assume found home sets belong to the current-user-principal
     *   - 1 or 2: We assume found home sets don't directly belong to the current-user-principal
     * @param alreadyQueriedPrincipals  The HttpUrls of principals which have been queried already, to avoid querying principals more than once.
     * @param alreadySavedHomeSets      The HttpUrls of home sets which have been saved to database already, to avoid saving home sets
     * more than once, which could overwrite the already set "personal" flag with `false`.
     *
     * @throws java.io.IOException                          on I/O errors
     * @throws HttpException                                on HTTP errors
     * @throws at.bitfire.dav4jvm.exception.DavException    on application-level or logical errors
     */
    internal fun discoverHomesets(
        principalUrl: HttpUrl,
        level: Int = 0,
        alreadyQueriedPrincipals: MutableSet<HttpUrl> = mutableSetOf(),
        alreadySavedHomeSets: MutableSet<HttpUrl> = mutableSetOf()
    ) {
        logger.fine("Discovering homesets of $principalUrl")
        val relatedResources = mutableSetOf<HttpUrl>()

        // Query the URL
        val principal = DavResource(httpClient, principalUrl)
        val personal = level == 0
        try {
            principal.propfind(0, *homeSetProperties) { davResponse, _ ->
                alreadyQueriedPrincipals += davResponse.href

                // If response holds home sets, save them
                davResponse[homeSetClass]?.let { homeSets ->
                    for (homeSetHref in homeSets.hrefs)
                        principal.location.resolve(homeSetHref)?.let { homesetUrl ->
                            val resolvedHomeSetUrl = UrlUtils.withTrailingSlash(homesetUrl)
                            if (!alreadySavedHomeSets.contains(resolvedHomeSetUrl)) {
                                homeSetRepository.insertOrUpdateByUrlBlocking(
                                    // HomeSet is considered personal if this is the outer recursion call,
                                    // This is because we assume the first call to query the current-user-principal
                                    // Note: This is not be be confused with the DAV:owner attribute. Home sets can be owned by
                                    // other principals while still being considered "personal" (belonging to the current-user-principal)
                                    // and an owned home set need not always be personal either.
                                    HomeSet(0, service.id, personal, resolvedHomeSetUrl)
                                )
                                alreadySavedHomeSets += resolvedHomeSetUrl
                            }
                        }
                }

                // Add related principals to be queried afterwards
                if (personal) {
                    val relatedResourcesTypes = listOf(
                        // current resource is a read/write-proxy for other principals
                        CalendarProxyReadFor::class.java,
                        CalendarProxyWriteFor::class.java,
                        // current resource is a member of a group (principal that can also have proxies)
                        GroupMembership::class.java
                    )
                    for (type in relatedResourcesTypes)
                        davResponse[type]?.let {
                            for (href in it.hrefs)
                                principal.location.resolve(href)?.let { url ->
                                    relatedResources += url
                                }
                        }
                }

                // If current resource is a calendar-proxy-read/write, it's likely that its parent is a principal, too.
                davResponse[ResourceType::class.java]?.let { resourceType ->
                    val proxyProperties = arrayOf(
                        ResourceType.CALENDAR_PROXY_READ,
                        ResourceType.CALENDAR_PROXY_WRITE,
                    )
                    if (proxyProperties.any { resourceType.types.contains(it) })
                        relatedResources += davResponse.href.parent()
                }
            }
        } catch (e: HttpException) {
            if (e.isClientError)
                logger.log(Level.INFO, "Ignoring Client Error 4xx while looking for ${service.type} home sets", e)
            else
                throw e
        }

        // query related resources
        if (level <= 1)
            for (resource in relatedResources)
                if (alreadyQueriedPrincipals.contains(resource))
                    logger.warning("$resource already queried, skipping")
                else
                    discoverHomesets(
                        principalUrl = resource,
                        level = level + 1,
                        alreadyQueriedPrincipals = alreadyQueriedPrincipals,
                        alreadySavedHomeSets = alreadySavedHomeSets
                    )
    }

}