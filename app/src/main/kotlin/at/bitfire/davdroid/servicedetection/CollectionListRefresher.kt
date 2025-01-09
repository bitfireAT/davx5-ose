/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.caldav.CalendarColor
import at.bitfire.dav4jvm.property.caldav.CalendarDescription
import at.bitfire.dav4jvm.property.caldav.CalendarHomeSet
import at.bitfire.dav4jvm.property.caldav.CalendarProxyReadFor
import at.bitfire.dav4jvm.property.caldav.CalendarProxyWriteFor
import at.bitfire.dav4jvm.property.caldav.CalendarTimezone
import at.bitfire.dav4jvm.property.caldav.CalendarTimezoneId
import at.bitfire.dav4jvm.property.caldav.Source
import at.bitfire.dav4jvm.property.caldav.SupportedCalendarComponentSet
import at.bitfire.dav4jvm.property.carddav.AddressbookDescription
import at.bitfire.dav4jvm.property.carddav.AddressbookHomeSet
import at.bitfire.dav4jvm.property.carddav.SupportedAddressData
import at.bitfire.dav4jvm.property.push.PushTransports
import at.bitfire.dav4jvm.property.push.Topic
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.GroupMembership
import at.bitfire.dav4jvm.property.webdav.HrefListProperty
import at.bitfire.dav4jvm.property.webdav.Owner
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Principal
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavHomeSetRepository
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.DavUtils.parent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Logic for refreshing the list of collections and home-sets and related information.
 */
class CollectionListRefresher @AssistedInject constructor(
    @Assisted val service: Service,
    @Assisted val httpClient: OkHttpClient,
    private val db: AppDatabase,
    private val collectionRepository: DavCollectionRepository,
    private val homeSetRepository: DavHomeSetRepository,
    private val logger: Logger,
    private val settings: SettingsManager
) {

    @AssistedFactory
    interface Factory {
        fun create(service: Service, httpClient: OkHttpClient): CollectionListRefresher
    }

    /**
     * Principal properties to ask the server for.
     */
    private val principalProperties = arrayOf(
        DisplayName.NAME,
        ResourceType.NAME
    )

    /**
     * Home Set class to use depending on the given service type.
     */
    private val homeSetClass: Class<out HrefListProperty> =
        when (service.type) {
            Service.TYPE_CARDDAV -> AddressbookHomeSet::class.java
            Service.TYPE_CALDAV -> CalendarHomeSet::class.java
            else -> throw IllegalArgumentException()
        }

    /**
     * Home Set properties to ask for in a propfind request to the CalDAV/CardDAV server,
     * depending on the given service type.
     */
    private val homeSetProperties: Array<Property.Name> =
        when (service.type) {
            Service.TYPE_CARDDAV -> arrayOf(
                DisplayName.NAME,
                AddressbookHomeSet.NAME,
                GroupMembership.NAME,
                ResourceType.NAME
            )
            Service.TYPE_CALDAV -> arrayOf(
                DisplayName.NAME,
                CalendarHomeSet.NAME,
                CalendarProxyReadFor.NAME,
                CalendarProxyWriteFor.NAME,
                GroupMembership.NAME,
                ResourceType.NAME
            )
            else -> throw IllegalArgumentException()
        }

    /**
     * Collection properties to ask for in a propfind request to the CalDAV/CardDAV server
     */
    private val collectionProperties = arrayOf(
        ResourceType.NAME,
        CurrentUserPrivilegeSet.NAME,
        DisplayName.NAME,
        Owner.NAME,
        AddressbookDescription.NAME, SupportedAddressData.NAME,
        CalendarDescription.NAME, CalendarColor.NAME, CalendarTimezone.NAME, CalendarTimezoneId.NAME, SupportedCalendarComponentSet.NAME,
        Source.NAME,
        // WebDAV Push
        PushTransports.NAME,
        Topic.NAME
    )


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
                                homeSetRepository.insertOrUpdateByUrl(
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
            if (e.code/100 == 4)
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

    /**
     * Refreshes home-sets and their collections.
     *
     * Each stored home-set URL is queried (`PROPFIND`) and its collections are either saved, updated
     * or marked as homeless - in case a collection was removed from its home-set.
     *
     * If a home-set URL in fact points to a collection directly, the collection will be saved with this URL,
     * and a null value for it's home-set. Refreshing of collections without home-sets is then handled by [refreshHomelessCollections].
     */
    internal fun refreshHomesetsAndTheirCollections() {
        val homesets = homeSetRepository.getByService(service.id).associateBy { it.url }.toMutableMap()
        for((homeSetUrl, localHomeset) in homesets) {
            logger.fine("Listing home set $homeSetUrl")

            // To find removed collections in this homeset: create a queue from existing collections and remove every collection that
            // is successfully rediscovered. If there are collections left, after processing is done, these are marked homeless.
            val localHomesetCollections = db.collectionDao()
                .getByServiceAndHomeset(service.id, localHomeset.id)
                .associateBy { it.url }
                .toMutableMap()

            try {
                DavResource(httpClient, homeSetUrl).propfind(1, *collectionProperties) { response, relation ->
                    // Note: This callback may be called multiple times ([MultiResponseCallback])
                    if (!response.isSuccess())
                        return@propfind

                    if (relation == Response.HrefRelation.SELF) {
                        // this response is about the homeset itself
                        localHomeset.displayName = response[DisplayName::class.java]?.displayName
                        localHomeset.privBind = response[CurrentUserPrivilegeSet::class.java]?.mayBind != false
                        homeSetRepository.insertOrUpdateByUrl(localHomeset)
                    }

                    // in any case, check whether the response is about a usable collection
                    val collection = Collection.fromDavResponse(response) ?: return@propfind

                    collection.serviceId = service.id
                    collection.homeSetId = localHomeset.id
                    collection.sync = shouldPreselect(collection, homesets.values)

                    // .. and save the principal url (collection owner)
                    response[Owner::class.java]?.href
                        ?.let { response.href.resolve(it) }
                        ?.let { principalUrl ->
                            val principal = Principal.fromServiceAndUrl(service, principalUrl)
                            val id = db.principalDao().insertOrUpdate(service.id, principal)
                            collection.ownerId = id
                        }

                    logger.log(Level.FINE, "Found collection", collection)

                    // save or update collection if usable (ignore it otherwise)
                    if (isUsableCollection(collection))
                        collectionRepository.insertOrUpdateByUrlAndRememberFlags(collection)

                    // Remove this collection from queue - because it was found in the home set
                    localHomesetCollections.remove(collection.url)
                }
            } catch (e: HttpException) {
                // delete home set locally if it was not accessible (40x)
                if (e.code in arrayOf(403, 404, 410))
                    homeSetRepository.delete(localHomeset)
            }

            // Mark leftover (not rediscovered) collections from queue as homeless (remove association)
            for ((_, homelessCollection) in localHomesetCollections) {
                homelessCollection.homeSetId = null
                collectionRepository.insertOrUpdateByUrlAndRememberFlags(homelessCollection)
            }

        }
    }

    /**
     * Refreshes collections which don't have a homeset.
     *
     * It queries each stored collection with a homeSetId of "null" and either updates or deletes (if inaccessible or unusable) them.
     */
    internal fun refreshHomelessCollections() {
        val homelessCollections = db.collectionDao().getByServiceAndHomeset(service.id, null).associateBy { it.url }.toMutableMap()
        for((url, localCollection) in homelessCollections) try {
            DavResource(httpClient, url).propfind(0, *collectionProperties) { response, _ ->
                if (!response.isSuccess()) {
                    collectionRepository.delete(localCollection)
                    return@propfind
                }

                // Save or update the collection, if usable, otherwise delete it
                Collection.fromDavResponse(response)?.let { collection ->
                    if (!isUsableCollection(collection))
                        return@let
                    collection.serviceId = localCollection.serviceId       // use same service ID as previous entry

                    // .. and save the principal url (collection owner)
                    response[Owner::class.java]?.href
                        ?.let { response.href.resolve(it) }
                        ?.let { principalUrl ->
                            val principal = Principal.fromServiceAndUrl(service, principalUrl)
                            val principalId = db.principalDao().insertOrUpdate(service.id, principal)
                            collection.ownerId = principalId
                        }

                    collectionRepository.insertOrUpdateByUrlAndRememberFlags(collection)
                } ?: collectionRepository.delete(localCollection)
            }
        } catch (e: HttpException) {
            // delete collection locally if it was not accessible (40x)
            if (e.code in arrayOf(403, 404, 410))
                collectionRepository.delete(localCollection)
            else
                throw e
        }

    }

    /**
     * Refreshes the principals (get their current display names).
     * Also removes principals which do not own any collections anymore.
     */
    internal fun refreshPrincipals() {
        // Refresh principals (collection owner urls)
        val principals = db.principalDao().getByService(service.id)
        for (oldPrincipal in principals) {
            val principalUrl = oldPrincipal.url
            logger.fine("Querying principal $principalUrl")
            try {
                DavResource(httpClient, principalUrl).propfind(0, *principalProperties) { response, _ ->
                    if (!response.isSuccess())
                        return@propfind
                    Principal.fromDavResponse(service.id, response)?.let { principal ->
                        logger.fine("Got principal: $principal")
                        db.principalDao().insertOrUpdate(service.id, principal)
                    }
                }
            } catch (e: HttpException) {
                logger.info("Principal update failed with response code ${e.code}. principalUrl=$principalUrl")
            }
        }

        // Delete principals which don't own any collections
        db.principalDao().getAllWithoutCollections().forEach {principal ->
            db.principalDao().delete(principal)
        }
    }

    /**
     * Finds out whether given collection is usable, by checking that either
     *  - CalDAV/CardDAV: service and collection type match, or
     *  - WebCal: subscription source URL is not empty
     */
    private fun isUsableCollection(collection: Collection) =
        (service.type == Service.TYPE_CARDDAV && collection.type == Collection.TYPE_ADDRESSBOOK) ||
                (service.type == Service.TYPE_CALDAV && arrayOf(Collection.TYPE_CALENDAR, Collection.TYPE_WEBCAL).contains(collection.type)) ||
                (collection.type == Collection.TYPE_WEBCAL && collection.source != null)

    /**
     * Whether to preselect the given collection for synchronisation, according to the
     * settings [Settings.PRESELECT_COLLECTIONS] (see there for allowed values) and
     * [Settings.PRESELECT_COLLECTIONS_EXCLUDED].
     *
     * A collection is considered _personal_ if it is found in one of the current-user-principal's home-sets.
     *
     * Before a collection is pre-selected, we check whether its URL matches the regexp in
     * [Settings.PRESELECT_COLLECTIONS_EXCLUDED], in which case *false* is returned.
     *
     * @param collection the collection to check
     * @param homeSets list of home-sets (to check whether collection is in a personal home-set)
     * @return *true* if the collection should be preselected for synchronization; *false* otherwise
     */
    internal fun shouldPreselect(collection: Collection, homeSets: Iterable<HomeSet>): Boolean {
        val shouldPreselect = settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS)

        val excluded by lazy {
            val excludedRegex = settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED)
            if (!excludedRegex.isNullOrEmpty())
                Regex(excludedRegex).containsMatchIn(collection.url.toString())
            else
                false
        }

        return when (shouldPreselect) {
            Settings.PRESELECT_COLLECTIONS_ALL ->
                // preselect if collection url is not excluded
                !excluded

            Settings.PRESELECT_COLLECTIONS_PERSONAL ->
                // preselect if is personal (in a personal home-set), but not excluded
                homeSets
                    .filter { homeset -> homeset.personal }
                    .map { homeset -> homeset.id }
                    .contains(collection.homeSetId)
                    && !excluded

            else -> // don't preselect
                false
        }
    }
}