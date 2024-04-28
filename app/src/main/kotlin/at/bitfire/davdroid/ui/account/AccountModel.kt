/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Application
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.caldav.NS_APPLE_ICAL
import at.bitfire.dav4jvm.property.caldav.NS_CALDAV
import at.bitfire.dav4jvm.property.carddav.NS_CARDDAV
import at.bitfire.dav4jvm.property.webdav.NS_WEBDAV
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.ical4android.util.DateUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.fortuna.ical4j.model.Calendar
import java.io.StringWriter
import java.util.Optional
import java.util.logging.Level

class AccountModel @AssistedInject constructor(
    val context: Application,
    private val db: AppDatabase,
    private val accountRepository: AccountRepository,
    private val collectionRepository: DavCollectionRepository,
    serviceRepository: DavServiceRepository,
    accountProgressUseCase: AccountProgressUseCase,
    getBindableHomesetsFromServiceUseCase: GetBindableHomeSetsFromServiceUseCase,
    getServiceCollectionPagerUseCase: GetServiceCollectionPagerUseCase,
    @Assisted val account: Account
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): AccountModel
    }

    companion object {
        fun factoryFromAccount(assistedFactory: Factory, account: Account) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return assistedFactory.create(account) as T
            }
        }
    }


    /** whether the account is invalid and the screen shall be closed */
    val invalidAccount = accountRepository.getAllFlow().map { accounts ->
        !accounts.contains(account)
    }

    private val settings = AccountSettings(context, account)
    private val refreshSettingsSignal = MutableLiveData(Unit)
    val showOnlyPersonal = refreshSettingsSignal.switchMap<Unit, AccountSettings.ShowOnlyPersonal> {
        object : LiveData<AccountSettings.ShowOnlyPersonal>() {
            init {
                viewModelScope.launch(Dispatchers.IO) {
                    postValue(settings.getShowOnlyPersonal())
                }
            }
        }
    }.asFlow()
    fun setShowOnlyPersonal(showOnlyPersonal: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        settings.setShowOnlyPersonal(showOnlyPersonal)
        refreshSettingsSignal.postValue(Unit)
    }

    val cardDavSvc = serviceRepository.getCardDavServiceFlow(account.name)
    val bindableAddressBookHomesets = getBindableHomesetsFromServiceUseCase(cardDavSvc)
    val canCreateAddressBook = bindableAddressBookHomesets.map { homeSets ->
        homeSets.isNotEmpty()
    }
    val cardDavProgress: Flow<AccountProgress> = accountProgressUseCase(
        account = account,
        serviceFlow = cardDavSvc,
        authoritiesFlow = flowOf(listOf(context.getString(R.string.address_books_authority)))
    )
    val addressBooksPager = getServiceCollectionPagerUseCase(cardDavSvc, Collection.TYPE_ADDRESSBOOK, showOnlyPersonal)

    val calDavSvc = serviceRepository.getCalDavServiceFlow(account.name)
    val bindableCalendarHomesets = getBindableHomesetsFromServiceUseCase(calDavSvc)
    val canCreateCalendar = bindableCalendarHomesets.map { homeSets ->
        homeSets.isNotEmpty()
    }
    private val tasksProvider = TaskUtils.currentProviderFlow(context, viewModelScope)
    private val calDavAuthorities = tasksProvider.map { tasks ->
        listOfNotNull(CalendarContract.AUTHORITY, tasks?.authority)
    }
    val calDavProgress = accountProgressUseCase(
        account = account,
        serviceFlow = calDavSvc,
        authoritiesFlow = calDavAuthorities
    )
    val calendarsPager = getServiceCollectionPagerUseCase(calDavSvc, Collection.TYPE_CALENDAR, showOnlyPersonal)
    val webcalPager = getServiceCollectionPagerUseCase(calDavSvc, Collection.TYPE_WEBCAL, showOnlyPersonal)

    var error by mutableStateOf<String?>(null)
        private set


    fun resetError() {
        error = null
    }


    // actions

    /**
     * Renames the [account] to given name.
     *
     * @param newName new account name
     */
    fun renameAccount(newName: String) {
        viewModelScope.launch {
            try {
                accountRepository.rename(account.name, newName)

                // synchronize again
                val newAccount = Account(context.getString(R.string.account_type), newName)
                OneTimeSyncWorker.enqueueAllAuthorities(context, newAccount, manual = true)
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't rename account", e)
                error = e.localizedMessage
            }
        }
    }

    /** Deletes the account from the system (won't touch collections on the server). */
    fun deleteAccount() {
        viewModelScope.launch {
            accountRepository.delete(account.name)
        }
    }


    val createCollectionResult = MutableLiveData<Optional<Exception>>()
    /**
     * Creates a WebDAV collection using MKCOL or MKCALENDAR.
     *
     * @param homeSet       home set into which the collection shall be created
     * @param addressBook   *true* if an address book shall be created, *false* if a calendar should be created
     * @param name          name (path segment) of the collection
     */
    fun createCollection(
        homeSet: HomeSet,
        addressBook: Boolean,
        name: String,
        displayName: String?,
        description: String?,
        color: Int? = null,
        timeZoneId: String? = null,
        supportsVEVENT: Boolean? = null,
        supportsVTODO: Boolean? = null,
        supportsVJOURNAL: Boolean? = null
    ) = viewModelScope.launch(Dispatchers.IO) {
        HttpClient.Builder(context, AccountSettings(context, account))
            .setForeground(true)
            .build().use { httpClient ->
                try {
                    // delete on server
                    val url = homeSet.url.newBuilder()
                        .addPathSegment(name)
                        .addPathSegment("")     // trailing slash
                        .build()
                    val dav = DavResource(httpClient.okHttpClient, url)

                    val xml = generateMkColXml(
                        addressBook = addressBook,
                        displayName = displayName,
                        description = description,
                        color = color,
                        timezoneDef = timeZoneId?.let { tzId ->
                            DateUtils.ical4jTimeZone(tzId)?.let { tz ->
                                val cal = Calendar()
                                cal.components += tz.vTimeZone
                                cal.toString()
                            }
                        },
                        supportsVEVENT = supportsVEVENT,
                        supportsVTODO = supportsVTODO,
                        supportsVJOURNAL = supportsVJOURNAL
                    )

                    dav.mkCol(
                        xmlBody = xml,
                        method = if (addressBook) "MKCOL" else "MKCALENDAR"
                    ) {
                        // success, otherwise an exception would have been thrown
                    }

                    // no HTTP error -> create collection locally
                    val collection = Collection(
                        serviceId = homeSet.serviceId,
                        homeSetId = homeSet.id,
                        url = url,
                        type = if (addressBook) Collection.TYPE_ADDRESSBOOK else Collection.TYPE_CALENDAR,
                        displayName = displayName,
                        description = description
                    )
                    db.collectionDao().insert(collection)

                    // trigger service detection (because the collection may actually have other properties than the ones we have inserted)
                    RefreshCollectionsWorker.enqueue(context, homeSet.serviceId)

                    // post success
                    createCollectionResult.postValue(Optional.empty())
                } catch (e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't create collection", e)
                    // post error
                    createCollectionResult.postValue(Optional.of(e))
                }
            }
    }

    private fun generateMkColXml(
        addressBook: Boolean,
        displayName: String?,
        description: String?,
        color: Int? = null,
        timezoneDef: String? = null,
        supportsVEVENT: Boolean? = null,
        supportsVTODO: Boolean? = null,
        supportsVJOURNAL: Boolean? = null
    ): String {
        val writer = StringWriter()
        val serializer = XmlUtils.newSerializer()
        serializer.apply {
            setOutput(writer)

            startDocument("UTF-8", null)
            setPrefix("", NS_WEBDAV)
            setPrefix("CAL", NS_CALDAV)
            setPrefix("CARD", NS_CARDDAV)

            if (addressBook)
                startTag(NS_WEBDAV, "mkcol")
            else
                startTag(NS_CALDAV, "mkcalendar")
            startTag(NS_WEBDAV, "set")
            startTag(NS_WEBDAV, "prop")

            startTag(NS_WEBDAV, "resourcetype")
            startTag(NS_WEBDAV, "collection")
            endTag(NS_WEBDAV, "collection")
            if (addressBook) {
                startTag(NS_CARDDAV, "addressbook")
                endTag(NS_CARDDAV, "addressbook")
            } else {
                startTag(NS_CALDAV, "calendar")
                endTag(NS_CALDAV, "calendar")
            }
            endTag(NS_WEBDAV, "resourcetype")

            displayName?.let {
                startTag(NS_WEBDAV, "displayname")
                text(it)
                endTag(NS_WEBDAV, "displayname")
            }

            if (addressBook) {
                // addressbook-specific properties
                description?.let {
                    startTag(NS_CARDDAV, "addressbook-description")
                    text(it)
                    endTag(NS_CARDDAV, "addressbook-description")
                }

            } else {
                // calendar-specific properties
                description?.let {
                    startTag(NS_CALDAV, "calendar-description")
                    text(it)
                    endTag(NS_CALDAV, "calendar-description")
                }
                color?.let {
                    startTag(NS_APPLE_ICAL, "calendar-color")
                    text(DavUtils.ARGBtoCalDAVColor(it))
                    endTag(NS_APPLE_ICAL, "calendar-color")
                }
                timezoneDef?.let {
                    startTag(NS_CALDAV, "calendar-timezone")
                    cdsect(it)
                    endTag(NS_CALDAV, "calendar-timezone")
                }

                if (supportsVEVENT != null || supportsVTODO != null || supportsVJOURNAL != null) {
                    // only if there's at least one explicitly supported calendar component set, otherwise don't include the property
                    if (supportsVEVENT != false) {
                        startTag(NS_CALDAV, "comp")
                        attribute(null, "name", "VEVENT")
                        endTag(NS_CALDAV, "comp")
                    }
                    if (supportsVTODO != false) {
                        startTag(NS_CALDAV, "comp")
                        attribute(null, "name", "VTODO")
                        endTag(NS_CALDAV, "comp")
                    }
                    if (supportsVJOURNAL != false) {
                        startTag(NS_CALDAV, "comp")
                        attribute(null, "name", "VJOURNAL")
                        endTag(NS_CALDAV, "comp")
                    }
                }
            }

            endTag(NS_WEBDAV, "prop")
            endTag(NS_WEBDAV, "set")
            if (addressBook)
                endTag(NS_WEBDAV, "mkcol")
            else
                endTag(NS_CALDAV, "mkcalendar")
            endDocument()
        }
        return writer.toString()
    }

    val deleteCollectionResult = MutableLiveData<Optional<Exception>>()
    /** Deletes the given collection from the database and the server. */
    fun deleteCollection(collection: Collection) = viewModelScope.launch(Dispatchers.IO) {
        HttpClient.Builder(context, AccountSettings(context, account))
            .setForeground(true)
            .build().use { httpClient ->
                try {
                    // delete on server
                    val davResource = DavResource(httpClient.okHttpClient, collection.url)
                    davResource.delete(null) {}

                    // delete in database
                    db.collectionDao().delete(collection)

                    // post success
                    deleteCollectionResult.postValue(Optional.empty())
                } catch (e: Exception) {
                    Logger.log.log(Level.SEVERE, "Couldn't delete collection", e)
                    // post error
                    deleteCollectionResult.postValue(Optional.of(e))
                }
            }
    }

    fun setCollectionSync(id: Long, sync: Boolean) {
        viewModelScope.launch {
            collectionRepository.setCollectionSync(id, sync)
        }
    }


    // helpers

    fun getCollectionOwner(collection: Collection): LiveData<String?> {
        val id = collection.ownerId ?: return MutableLiveData(null)
        return db.principalDao().getLive(id).map { principal ->
            if (principal == null)
                return@map null
            principal.displayName ?: principal.url.toString()
        }
    }

    fun getCollectionLastSynced(collection: Collection): LiveData<Map<String, Long>> {
        return db.syncStatsDao().getLiveByCollectionId(collection.id).map { syncStatsList ->
            val syncStatsMap = syncStatsList.associateBy { it.authority }
            val interestingAuthorities = listOfNotNull(
                ContactsContract.AUTHORITY,
                CalendarContract.AUTHORITY,
                TaskUtils.currentProvider(context)?.authority
            )
            val result = mutableMapOf<String, Long>()
            for (authority in interestingAuthorities) {
                val lastSync = syncStatsMap[authority]?.lastSync
                if (lastSync != null)
                    result[getAppNameFromAuthority(authority)] = lastSync
            }
            result
        }
    }

    /**
     * Tries to find the application name for given authority. Returns the authority if not
     * found.
     *
     * @param authority authority to find the application name for (ie "at.techbee.jtx")
     * @return the application name of authority (ie "jtx Board")
     */
    private fun getAppNameFromAuthority(authority: String): String {
        val packageManager = context.packageManager
        val packageName = packageManager.resolveContentProvider(authority, 0)?.packageName ?: authority
        return try {
            val appInfo = packageManager.getPackageInfo(packageName, 0).applicationInfo
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Logger.log.warning("Application name not found for authority: $authority")
            authority
        }
    }

}