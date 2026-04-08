/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.resource.LocalCalendarStore
import at.bitfire.davdroid.resource.LocalDataStore
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.util.DavUtils.lastSegment
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dmfs.tasks.contract.TaskContract
import java.util.logging.Level
import java.util.logging.Logger

@HiltViewModel(assistedFactory = CollectionScreenModel.Factory::class)
class CollectionScreenModel @AssistedInject constructor(
    @ApplicationContext val context: Context,
    private val accountRepository: AccountRepository,
    private val accountSettingsFactory: AccountSettings.Factory,
    @Assisted val collectionId: Long,
    private val collectionRepository: DavCollectionRepository,
    private val collectionSelectedUseCase: Lazy<CollectionSelectedUseCase>,
    db: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val localAddressBookStore: Lazy<LocalAddressBookStore>,
    private val localCalendarStore: Lazy<LocalCalendarStore>,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository,
    private val tasksAppManager: Lazy<TasksAppManager>,
    settings: SettingsManager,
    syncStatsRepository: DavSyncStatsRepository
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(collectionId: Long): CollectionScreenModel
    }

    /** Whether an operation (like deleting the collection) is currently in progress */
    var inProgress by mutableStateOf(false)
        private set

    var invalid by mutableStateOf(false)
    var error by mutableStateOf<Exception?>(null)
        private set

    val collection = collectionRepository.getFlow(collectionId)
        .map {
            if (it == null)
                invalid = true
            it
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Flow that provides the account associated with the current collection */
    val account: Flow<Account?> = collection.filterNotNull().map { collection ->
        val service = serviceRepository.get(collection.serviceId)
        service?.let { accountRepository.fromName(it.accountName) }
    }


    enum class ReadOnlyState {
        READ_ONLY_BY_SETTING,
        READ_ONLY_BY_SERVER,
        READ_ONLY_BY_USER,
        READ_WRITE;

        fun canUserChange() = this == READ_WRITE || this == READ_ONLY_BY_USER
        fun isReadOnly() = this != READ_WRITE
    }

    /** whether address-books are read-only by policy (if yes, it overrides everything else) */
    private val forceReadOnlyAddressBooks = settings.getBooleanFlow(Settings.FORCE_READ_ONLY_ADDRESSBOOKS, false)

    val readOnly: Flow<ReadOnlyState> = combine(collection, forceReadOnlyAddressBooks) { collection, forceReadOnlyAddressBook ->
        when {
            collection?.type == Collection.TYPE_ADDRESSBOOK && forceReadOnlyAddressBook ->
                ReadOnlyState.READ_ONLY_BY_SETTING
            collection?.privWriteContent == false ->
                ReadOnlyState.READ_ONLY_BY_SERVER
            collection?.forceReadOnly == true ->
                ReadOnlyState.READ_ONLY_BY_USER
            else ->
                ReadOnlyState.READ_WRITE
        }
    }


    private val principalDao = db.principalDao()
    val owner: Flow<String?> = collection.map { collection ->
        collection?.ownerId?.let { ownerId ->
            val principal = principalDao.getAsync(ownerId)
            principal.displayName ?: principal.url.lastSegment
        }
    }


    val lastSynced = syncStatsRepository.getLastSyncedFlow(collectionId)

    /** The account's "past event time limit", or null if not set or not relevant for the collection. */
    val pastEventTimeLimit = combine(collection.filterNotNull(), account.filterNotNull()) { collection, account ->
        if (collection.type == Collection.TYPE_CALENDAR) {
            val accountSettings = withContext(ioDispatcher) {
                accountSettingsFactory.create(account)
            }
            accountSettings.getTimeRangePastDays()
        } else  // doesn't apply to address books
            null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val localItemCounts: Flow<List<LocalItemsCount>> = combine(
        collection.filterNotNull(),
        account.filterNotNull()
    ) { collection, account ->
        countLocalItemsFlow(collection, account)
    }.flatMapLatest { it }



    /** Scope for operations that must not be cancelled. */
    private val noCancellationScope = CoroutineScope(SupervisorJob())

    /**
     * Deletes the collection from the database and the server.
     */
    fun delete() {
        val collection = collection.value ?: return

        inProgress = true
        noCancellationScope.launch {
            try {
                collectionRepository.deleteRemote(collection)
            } catch (e: Exception) {
                error = e
            } finally {
                inProgress = false
            }
        }
    }

    fun resetError() {
        error = null
    }

    fun setForceReadOnly(forceReadOnly: Boolean) {
        viewModelScope.launch {
            collectionRepository.setForceReadOnly(collectionId, forceReadOnly)
            collectionSelectedUseCase.get().handleWithDelay(collectionId)
        }
    }

    fun setSync(sync: Boolean) {
        viewModelScope.launch {
            collectionRepository.setSync(collectionId, sync)
            collectionSelectedUseCase.get().handleWithDelay(collectionId)
        }
    }


    data class LocalItemsCount(
        /** Display name of content provider where the items are stored */
        val contentProviderName: String,

        /** Total number of items (including modified and deleted ones) */
        val total: Int,
        /** Number of unsynced local modifications */
        val modified: Int,
        /** Number of unsynced local deletions */
        val deleted: Int
    )

    private fun countLocalItemsFlow(collection: Collection, account: Account): Flow<List<LocalItemsCount>> {
        // Build one flow per local data store relevant to this collection type (will later be combined).
        val storeFlows: List<Flow<LocalItemsCount?>> = when (collection.type) {
            Collection.TYPE_ADDRESSBOOK -> listOf(
                observeLocalDataStore(
                    account = account,
                    localDataStore = localAddressBookStore.get(),
                    watchUris = listOf(ContactsContract.RawContacts.CONTENT_URI)
                )
            )
            Collection.TYPE_CALENDAR -> buildList {
                add(observeLocalDataStore(
                    account = account,
                    localDataStore = localCalendarStore.get(),
                    watchUris = listOf(
                        CalendarContract.Calendars.CONTENT_URI,
                        CalendarContract.Events.CONTENT_URI
                    )
                ))

                tasksAppManager.get().getDataStore()?.let { taskListStore ->
                    add(observeLocalDataStore(
                        account = account,
                        localDataStore = taskListStore,
                        watchUris = listOf(TaskContract.getContentUri(taskListStore.authority))
                    ))
                }
            }
            else -> emptyList()
        }

        return if (storeFlows.isEmpty())
            emptyFlow()
        else
            combine(storeFlows) { localItemsCounts -> localItemsCounts.filterNotNull() }
    }

    private fun observeLocalDataStore(
        account: Account,
        localDataStore: LocalDataStore<*>,
        watchUris: List<Uri>
    ): Flow<LocalItemsCount?> = callbackFlow {
        val client: ContentProviderClient = try {
            localDataStore.acquireContentProvider()
        } catch (e: SecurityException) {
            logger.log(Level.WARNING, "No permission to access data store", e)
            null
        } ?: run {
            // Emit null so that combine() can still emit values from other flows (e.g. calendar
            // stats can still be shown even when tasks permissions are missing).
            trySendBlocking(null)

            // Properly close flow to avoid "missing awaitClose" exception at runtime.
            close()
            return@callbackFlow
        }

        fun queryAndSend() {
            val count = localDataStore.getByDbCollectionId(account, client, collectionId)?.let { store ->
                LocalItemsCount(
                    contentProviderName = getProviderAppName(localDataStore.authority),
                    total = store.countAll(),
                    modified = store.countModified(),
                    deleted = store.countDeleted()
                )
            }
            trySendBlocking(count)
        }

        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) = queryAndSend()
            override fun onChange(selfChange: Boolean, uri: Uri?) = queryAndSend()
            override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) = queryAndSend()
        }

        client.use {
            logger.fine("Watching ${localDataStore.authority} for changes")
            /* It seems to be OK to register the same observer object for multiple URIs and then
                unregister it only once for all URIs. */
            for (uri in watchUris)
                context.contentResolver.registerContentObserver(uri, true, observer)

            queryAndSend()  // initial count
        }

        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    private fun getProviderAppName(authority: String): String {
        val packageManager = context.packageManager
        val providerInfo = packageManager.resolveContentProvider(authority, 0)
        val applicationInfo = providerInfo?.applicationInfo
        return if (applicationInfo != null)
            packageManager.getApplicationLabel(applicationInfo).toString()
        else
            authority
    }

}