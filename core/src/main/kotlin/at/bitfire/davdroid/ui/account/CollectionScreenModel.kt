/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
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
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.resource.LocalCalendarStore
import at.bitfire.davdroid.resource.LocalDataStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.dmfs.tasks.contract.TaskContract
import java.util.logging.Level
import java.util.logging.Logger

@HiltViewModel(assistedFactory = CollectionScreenModel.Factory::class)
class CollectionScreenModel @AssistedInject constructor(
    @ApplicationContext val context: Context,
    private val accountRepository: AccountRepository,
    @Assisted val collectionId: Long,
    db: AppDatabase,
    private val collectionRepository: DavCollectionRepository,
    private val collectionSelectedUseCase: Lazy<CollectionSelectedUseCase>,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val localItemCounts: Flow<List<LocalItemsCount>> = collection.filterNotNull().flatMapLatest { collection ->
        countLocalItemsFlow(collection)
    }


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
        /** display name of content provider where the items are stored */
        val contentProviderName: String,

        /** total number of items (including modified and deleted ones) */
        val total: Int?,
        /** number of unsynced local modifications */
        val modified: Int?,
        /** number of unsynced local deletions */
        val deleted: Int?
    )

    private fun countLocalItemsFlow(collection: Collection): Flow<List<LocalItemsCount>> = callbackFlow {
        val result = mutableMapOf<String, LocalItemsCount>()
        val contentResolver = context.contentResolver

        val service = serviceRepository.get(collection.serviceId) ?: return@callbackFlow
        val account = accountRepository.fromName(service.accountName)

        val registeredObservers = mutableListOf<ContentObserver>()

        // TODO For now only calendars/events.
        // TODO permissions

        // Unified callback creator for both address books and calendars
        val createOnUpdateCallback = { localDataStore: LocalDataStore<*> ->
            { localItemsCount: LocalItemsCount? ->
                if (localItemsCount != null)
                    result[localDataStore.authority] = localItemsCount
                else
                    result -= localDataStore.authority
                trySendBlocking(result.values.toList())
                Unit
            }
        }

        when (collection.type) {
            Collection.TYPE_ADDRESSBOOK -> {
                val addressBookStore = localAddressBookStore.get()
                val observer = registerLocalDataStoreObserver(
                    account = account,
                    localDataStore = addressBookStore,
                    watchUris = listOf(
                        ContactsContract.RawContacts.CONTENT_URI
                    ),
                    selectionModified = "${ContactsContract.RawContacts.DIRTY}=1 AND ${ContactsContract.RawContacts.DELETED}=0",
                    selectionDeleted = "${ContactsContract.RawContacts.DELETED}=1",
                    onUpdate = createOnUpdateCallback(addressBookStore)
                )
                registeredObservers.add(observer)
            }

            Collection.TYPE_CALENDAR -> {
                val calendarStore = localCalendarStore.get()
                val eventsObserver = registerLocalDataStoreObserver(
                    account = account,
                    localDataStore = calendarStore,
                    watchUris = listOf(
                        CalendarContract.Calendars.CONTENT_URI,
                        CalendarContract.Events.CONTENT_URI
                    ),
                    selectionModified = "${CalendarContract.Events.DIRTY}=1 AND ${CalendarContract.Events.DELETED}=0",
                    selectionDeleted = "${CalendarContract.Events.DELETED}=1",
                    onUpdate = createOnUpdateCallback(calendarStore)
                )
                registeredObservers.add(eventsObserver)

                tasksAppManager.get().getDataStore()?.let { taskListStore ->
                    val tasksObserver = registerLocalDataStoreObserver(
                        account = account,
                        localDataStore = taskListStore,
                        watchUris = listOf(
                            TaskContract.getContentUri(taskListStore.authority)
                        ),
                        selectionModified = "${TaskContract.Tasks._DIRTY}=1 AND ${TaskContract.Tasks._DELETED}=0",
                        selectionDeleted = "${TaskContract.Tasks._DELETED}=1",
                        onUpdate = createOnUpdateCallback(taskListStore)
                    )
                    registeredObservers.add(tasksObserver)
                }
            }
        }

        // Run observers to initially count the items
        for (observer in registeredObservers)
            observer.onChange(false)

        // Unregister all observers when Flow is closed
        awaitClose {
            registeredObservers.forEach {
                contentResolver.unregisterContentObserver(it)
            }
        }
    }

    private fun registerLocalDataStoreObserver(
        account: Account,
        localDataStore: LocalDataStore<*>,
        watchUris: List<Uri>,
        selectionModified: String,
        selectionDeleted: String,
        onUpdate: (LocalItemsCount?) -> Unit
    ): ContentObserver {
        val contentResolver = context.contentResolver
        val observer = createLocalDataStoreObserver(account, localDataStore, selectionModified, selectionDeleted, onUpdate)
        for (uri in watchUris)
            contentResolver.registerContentObserver(uri, true, observer)
        return observer
    }

    private fun createLocalDataStoreObserver(
        account: Account,
        localDataStore: LocalDataStore<*>,
        selectionModified: String,
        selectionDeleted: String,
        onUpdate: (LocalItemsCount?) -> Unit
    ): ContentObserver = object : ContentObserver(null) {

        /**
         * Counts the items in [localDataStore] and submits the result as [LocalItemsCount] to [onUpdate].
         */
        private fun update() {
            var result: LocalItemsCount? = null

            try {
                val authority = localDataStore.authority
                localDataStore.acquireContentProvider()?.use { client ->
                    localDataStore.getByDbCollectionId(account, client, collectionId)?.let { calendar ->
                        result = LocalItemsCount(
                            contentProviderName = getProviderAppName(authority),
                            total = calendar.count(),
                            modified = calendar.count(selectionModified),
                            deleted = calendar.count(selectionDeleted)
                        )
                    }
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't query local data store items", e)
            }

            // send a copy of the result – the flow wouldn't emit the same value again
            onUpdate(result)
        }

        override fun onChange(selfChange: Boolean) = update()
        override fun onChange(selfChange: Boolean, uri: Uri?) = update()
        override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) = update()

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