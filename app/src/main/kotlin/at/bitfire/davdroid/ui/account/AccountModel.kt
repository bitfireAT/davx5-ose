package at.bitfire.davdroid.ui.account

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.work.WorkInfo
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.caldav.NS_APPLE_ICAL
import at.bitfire.dav4jvm.property.caldav.NS_CALDAV
import at.bitfire.dav4jvm.property.carddav.NS_CARDDAV
import at.bitfire.dav4jvm.property.webdav.NS_WEBDAV
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.AccountsCleanupWorker
import at.bitfire.davdroid.syncadapter.BaseSyncWorker
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import at.bitfire.davdroid.syncadapter.PeriodicSyncWorker
import at.bitfire.davdroid.util.DavUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.io.StringWriter
import java.util.Optional
import java.util.logging.Level

class AccountModel @AssistedInject constructor(
    val context: Application,
    val db: AppDatabase,
    @Assisted val account: Account
): ViewModel(), OnAccountsUpdateListener {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): AccountModel
    }

    companion object {
        const val PAGER_SIZE = 20
    }

    /** whether the account is invalid and the AccountActivity shall be closed */
    val invalid = MutableLiveData<Boolean>()

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
    }
    fun setShowOnlyPersonal(showOnlyPersonal: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        settings.setShowOnlyPersonal(showOnlyPersonal)
        refreshSettingsSignal.postValue(Unit)
    }

    val accountManager: AccountManager = AccountManager.get(context)

    val cardDavSvc = db.serviceDao().getLiveByAccountAndType(account.name, Service.TYPE_CARDDAV)
    val bindableAddressBookHomesets = cardDavSvc.switchMap { svc ->
        if (svc != null)
            db.homeSetDao().getLiveBindableByService(svc.id)
        else
            MutableLiveData(emptyList())
    }
    val canCreateAddressBook = bindableAddressBookHomesets.map { homeSets ->
        homeSets.isNotEmpty()
    }
    val cardDavRefreshing = cardDavSvc.switchMap { svc ->
        if (svc == null)
            return@switchMap null
        RefreshCollectionsWorker.exists(context, RefreshCollectionsWorker.workerName(svc.id))
    }
    val cardDavSyncPending = BaseSyncWorker.exists(
        context,
        listOf(WorkInfo.State.ENQUEUED),
        account,
        listOf(context.getString(R.string.address_books_authority)),
        whichTag = { account, authority ->
            // we are only interested in pending OneTimeSyncWorkers because there's always a pending PeriodicSyncWorker
            OneTimeSyncWorker.workerName(account, authority)
        }
    )
    val cardDavSyncing = BaseSyncWorker.exists(
        context,
        listOf(WorkInfo.State.RUNNING),
        account,
        listOf(context.getString(R.string.address_books_authority))
    )
    val addressBooksPager = CollectionPager(db, cardDavSvc, Collection.TYPE_ADDRESSBOOK, showOnlyPersonal)

    private val tasksProvider = TaskUtils.currentProviderLive(context)
    val calDavSvc = db.serviceDao().getLiveByAccountAndType(account.name, Service.TYPE_CALDAV)
    val bindableCalendarHomesets = calDavSvc.switchMap { svc ->
        if (svc != null)
            db.homeSetDao().getLiveBindableByService(svc.id)
        else
            MutableLiveData(emptyList())
    }
    val canCreateCalendar = bindableCalendarHomesets.map { homeSets ->
        homeSets.isNotEmpty()
    }
    val calDavRefreshing = calDavSvc.switchMap { svc ->
        if (svc == null)
            return@switchMap null
        RefreshCollectionsWorker.exists(context, RefreshCollectionsWorker.workerName(svc.id))
    }
    val calDavSyncPending = tasksProvider.switchMap { tasks ->
        BaseSyncWorker.exists(
            context,
            listOf(WorkInfo.State.ENQUEUED),
            account,
            listOfNotNull(CalendarContract.AUTHORITY, tasks?.authority),
            whichTag = { account, authority ->
                // we are only interested in pending OneTimeSyncWorkers because there's always a pending PeriodicSyncWorker
                OneTimeSyncWorker.workerName(account, authority)
            }
        )
    }
    val calDavSyncing = tasksProvider.switchMap { tasks ->
        BaseSyncWorker.exists(
            context,
            listOf(WorkInfo.State.RUNNING),
            account,
            listOfNotNull(CalendarContract.AUTHORITY, tasks?.authority)
        )
    }
    val calendarsPager = CollectionPager(db, calDavSvc, Collection.TYPE_CALENDAR, showOnlyPersonal)
    val webcalPager = CollectionPager(db, calDavSvc, Collection.TYPE_WEBCAL, showOnlyPersonal)

    val renameAccountError = MutableLiveData<String>()


    init {
        accountManager.addOnAccountsUpdatedListener(this, null, true)
    }

    override fun onCleared() {
        super.onCleared()
        accountManager.removeOnAccountsUpdatedListener(this)
    }

    override fun onAccountsUpdated(accounts: Array<out Account>) {
        if (!accounts.contains(account))
            invalid.postValue(true)
    }


    // actions

    /**
     * Will try to rename the [account] to given name.
     *
     * @param newName new account name
     */
    fun renameAccount(newName: String) {
        val oldAccount = account

        // remember sync intervals
        val oldSettings = try {
            AccountSettings(context, oldAccount)
        } catch (e: InvalidAccountException) {
            renameAccountError.postValue(context.getString(R.string.account_invalid))
            invalid.postValue(true)
            return
        }

        val authorities = mutableListOf(
            context.getString(R.string.address_books_authority),
            CalendarContract.AUTHORITY
        )
        tasksProvider.value?.authority?.let { authorities.add(it) }
        val syncIntervals = authorities.map { Pair(it, oldSettings.getSyncInterval(it)) }

        val accountManager = AccountManager.get(context)
        // check whether name is already taken
        if (accountManager.getAccountsByType(context.getString(R.string.account_type)).map { it.name }.contains(newName)) {
            Logger.log.log(Level.WARNING, "Account with name \"$newName\" already exists")
            renameAccountError.postValue(context.getString(R.string.account_rename_exists_already))
            return
        }

        try {
            /* https://github.com/bitfireAT/davx5/issues/135
            Lock accounts cleanup so that the AccountsCleanupWorker doesn't run while we rename the account
            because this can cause problems when:
            1. The account is renamed.
            2. The AccountsCleanupWorker is called BEFORE the services table is updated.
               → AccountsCleanupWorker removes the "orphaned" services because they belong to the old account which doesn't exist anymore
            3. Now the services would be renamed, but they're not here anymore. */
            AccountsCleanupWorker.lockAccountsCleanup()

            // Renaming account
            accountManager.renameAccount(oldAccount, newName, @MainThread {
                if (it.result?.name == newName /* account has new name -> success */)
                    viewModelScope.launch(Dispatchers.Default + NonCancellable) {
                        try {
                            onAccountRenamed(accountManager, oldAccount, newName, syncIntervals)
                        } finally {
                            // release AccountsCleanupWorker mutex at the end of this async coroutine
                            AccountsCleanupWorker.unlockAccountsCleanup()
                        }
                    } else
                // release AccountsCleanupWorker mutex now
                    AccountsCleanupWorker.unlockAccountsCleanup()

                // close AccountActivity with old name
                invalid.postValue(true)
            }, null)
        } catch (e: Exception) {
            Logger.log.log(Level.WARNING, "Couldn't rename account", e)
            renameAccountError.postValue(context.getString(R.string.account_rename_couldnt_rename))
        }
    }

    /**
     * Called when an account has been renamed.
     *
     * @param oldAccount the old account
     * @param newName the new account
     * @param syncIntervals map with entries of type (authority -> sync interval) of the old account
     */
    @SuppressLint("Recycle")
    @WorkerThread
    fun onAccountRenamed(accountManager: AccountManager, oldAccount: Account, newName: String, syncIntervals: List<Pair<String, Long?>>) {
        // account has now been renamed
        Logger.log.info("Updating account name references")

        // disable periodic workers of old account
        syncIntervals.forEach { (authority, _) ->
            PeriodicSyncWorker.disable(context, oldAccount, authority)
        }

        // cancel maybe running synchronization
        BaseSyncWorker.cancelAllWork(context, oldAccount)
        /*for (addrBookAccount in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)))
            SyncWorker.cancelSync(context, addrBookAccount)*/

        // update account name references in database
        try {
            db.serviceDao().renameAccount(oldAccount.name, newName)
        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't update service DB", e)
            renameAccountError.postValue(context.getString(R.string.account_rename_couldnt_rename))
            return
        }

        // update main account of address book accounts
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED)
            try {
                context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)?.use { provider ->
                    for (addrBookAccount in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))) {
                        val addressBook = LocalAddressBook(context, addrBookAccount, provider)
                        if (oldAccount == addressBook.mainAccount)
                            addressBook.mainAccount = Account(newName, oldAccount.type)
                    }
                }
            } catch (e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't update address book accounts", e)
            }

        // calendar provider doesn't allow changing account_name of Events
        // (all events will have to be downloaded again)

        // update account_name of local tasks
        try {
            LocalTaskList.onRenameAccount(context, oldAccount.name, newName)
        } catch (e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't propagate new account name to tasks provider", e)
        }

        // retain sync intervals
        val newAccount = Account(newName, oldAccount.type)
        val newSettings = AccountSettings(context, newAccount)
        for ((authority, interval) in syncIntervals) {
            if (interval == null)
                ContentResolver.setIsSyncable(newAccount, authority, 0)
            else {
                ContentResolver.setIsSyncable(newAccount, authority, 1)
                newSettings.setSyncInterval(authority, interval)
            }
        }

        // synchronize again
        OneTimeSyncWorker.enqueueAllAuthorities(context, newAccount, manual = true)
    }


    /** Deletes the account from the system (won't touch collections on the server). */
    fun deleteAccount() {
        val accountManager = AccountManager.get(context)
        accountManager.removeAccount(account, null, { future ->
            try {
                if (future.result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT))
                    Handler(Looper.getMainLooper()).post {
                        invalid.postValue(true)
                    }
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't remove account", e)
            }
        }, null)
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
        description: String?
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
                        description = description
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

    fun setCollectionSync(id: Long, sync: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        db.collectionDao().updateSync(id, sync)
    }

    fun setCollectionForceReadOnly(id: Long, forceReadOnly: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        db.collectionDao().updateForceReadOnly(id, forceReadOnly)
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


    class CollectionPager(
        val db: AppDatabase,
        service: LiveData<Service?>,
        val collectionType: String,
        showOnlyPersonal: LiveData<AccountSettings.ShowOnlyPersonal>
    ) : MediatorLiveData<Pager<Int, Collection>?>() {

        var _serviceId: Long? = null
        var _onlyPersonal: Boolean? = null

        init {
            addSource(service) {
                _serviceId = it?.id
                calculate()
            }
            addSource(showOnlyPersonal) {
                _onlyPersonal = it.onlyPersonal
                calculate()
            }
        }

        fun calculate() {
            val serviceId = _serviceId ?: return
            val onlyPersonal = _onlyPersonal ?: return
            value = Pager(
                config = PagingConfig(PAGER_SIZE),
                pagingSourceFactory = {
                    if (onlyPersonal)
                        db.collectionDao().pagePersonalByServiceAndType(serviceId, collectionType)
                    else
                        db.collectionDao().pageByServiceAndType(serviceId, collectionType)
                }
            )
        }

    }

}