package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Application
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.work.WorkInfo
import at.bitfire.dav4jvm.DavResource
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.SyncWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Optional
import java.util.logging.Level

class AccountModel @AssistedInject constructor(
    application: Application,
    val db: AppDatabase,
    @Assisted val account: Account
): AndroidViewModel(application), OnAccountsUpdateListener {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): AccountModel
    }

    companion object {
        const val PAGER_SIZE = 20
    }

    /** whether the account is invalid and the AccountActivity shall be closed */
    val invalid = MutableLiveData<Boolean>()

    private val settings = AccountSettings(application, account)
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

    val context = getApplication<Application>()
    val accountManager: AccountManager = AccountManager.get(context)

    val cardDavSvc = db.serviceDao().getLiveByAccountAndType(account.name, Service.TYPE_CARDDAV)
    val cardDavRefreshingActive = cardDavSvc.switchMap { svc ->
        if (svc == null)
            return@switchMap null
        RefreshCollectionsWorker.exists(application, RefreshCollectionsWorker.workerName(svc.id))
    }
    val cardDavSyncPending = SyncWorker.exists(
        getApplication(),
        listOf(WorkInfo.State.ENQUEUED),
        account,
        listOf(context.getString(R.string.address_books_authority), ContactsContract.AUTHORITY)
    )
    val cardDavSyncActive = SyncWorker.exists(
        getApplication(),
        listOf(WorkInfo.State.RUNNING),
        account,
        listOf(context.getString(R.string.address_books_authority), ContactsContract.AUTHORITY)
    )
    val addressBooksPager = CollectionPager(db, cardDavSvc, Collection.TYPE_ADDRESSBOOK, showOnlyPersonal)

    val calDavSvc = db.serviceDao().getLiveByAccountAndType(account.name, Service.TYPE_CALDAV)
    val calDavRefreshingActive = calDavSvc.switchMap { svc ->
        if (svc == null)
            return@switchMap null
        RefreshCollectionsWorker.exists(application, RefreshCollectionsWorker.workerName(svc.id))
    }
    val calDavSyncPending = SyncWorker.exists(
        getApplication(),
        listOf(WorkInfo.State.ENQUEUED),
        account,
        listOf(CalendarContract.AUTHORITY)
    )
    val calDavSyncActive = SyncWorker.exists(
        getApplication(),
        listOf(WorkInfo.State.RUNNING),
        account,
        listOf(CalendarContract.AUTHORITY)
    )
    val calendarsPager = CollectionPager(db, calDavSvc, Collection.TYPE_CALENDAR, showOnlyPersonal)
    val webcalPager = CollectionPager(db, calDavSvc, Collection.TYPE_WEBCAL, showOnlyPersonal)

    val deleteCollectionResult = MutableLiveData<Optional<Exception>>()


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

    /** Deletes the given collection from the database and the server. */
    fun deleteCollection(collection: Collection) = viewModelScope.launch(Dispatchers.IO) {
        HttpClient.Builder(getApplication(), AccountSettings(getApplication(), account))
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
                TaskUtils.currentProvider(getApplication())?.authority
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
        val packageManager = getApplication<Application>().packageManager
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