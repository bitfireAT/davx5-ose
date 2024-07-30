/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Context
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

@HiltViewModel(assistedFactory = AccountScreenModel.Factory::class)
class AccountScreenModel @AssistedInject constructor(
    @Assisted val account: Account,
    private val accountRepository: AccountRepository,
    accountProgressUseCase: AccountProgressUseCase,
    accountSettingsFactory: AccountSettings.Factory,
    private val collectionRepository: DavCollectionRepository,
    @ApplicationContext val context: Context,
    getBindableHomesetsFromService: GetBindableHomeSetsFromServiceUseCase,
    getServiceCollectionPager: GetServiceCollectionPagerUseCase,
    private val logger: Logger,
    serviceRepository: DavServiceRepository,
    private val tasksAppManager: TasksAppManager
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(account: Account): AccountScreenModel
    }

    /** whether the account is invalid and the screen shall be closed */
    val invalidAccount = accountRepository.getAllFlow().map { accounts ->
        !accounts.contains(account)
    }

    private val settings = accountSettingsFactory.forAccount(account)
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

    val cardDavSvc = serviceRepository
        .getCardDavServiceFlow(account.name)
        .stateIn(viewModelScope, initialValue = null, started = SharingStarted.Eagerly)
    private val bindableAddressBookHomesets = getBindableHomesetsFromService(cardDavSvc)
    val canCreateAddressBook = bindableAddressBookHomesets.map { homeSets ->
        homeSets.isNotEmpty()
    }
    val cardDavProgress: Flow<AccountProgress> = accountProgressUseCase(
        account = account,
        serviceFlow = cardDavSvc,
        authoritiesFlow = flowOf(listOf(context.getString(R.string.address_books_authority)))
    )
    val addressBooks = getServiceCollectionPager(cardDavSvc, Collection.TYPE_ADDRESSBOOK, showOnlyPersonal)

    val calDavSvc = serviceRepository
        .getCalDavServiceFlow(account.name)
        .stateIn(viewModelScope, initialValue = null, started = SharingStarted.Eagerly)
    private val bindableCalendarHomesets = getBindableHomesetsFromService(calDavSvc)
    val canCreateCalendar = bindableCalendarHomesets.map { homeSets ->
        homeSets.isNotEmpty()
    }
    val tasksProvider = tasksAppManager.currentProviderFlow(viewModelScope)
    private val calDavAuthorities = tasksProvider.map { tasks ->
        listOfNotNull(CalendarContract.AUTHORITY, tasks?.authority)
    }
    val calDavProgress = accountProgressUseCase(
        account = account,
        serviceFlow = calDavSvc,
        authoritiesFlow = calDavAuthorities
    )
    val calendars = getServiceCollectionPager(calDavSvc, Collection.TYPE_CALENDAR, showOnlyPersonal)
    val subscriptions = getServiceCollectionPager(calDavSvc, Collection.TYPE_WEBCAL, showOnlyPersonal)


    var error by mutableStateOf<String?>(null)
        private set

    fun resetError() { error = null }


    var showNoWebcalApp by mutableStateOf(false)
        private set

    fun noWebcalApp() { showNoWebcalApp = true }
    fun resetShowNoWebcalApp() { showNoWebcalApp = false }


    // actions

    private val notInterruptibleScope = CoroutineScope(SupervisorJob())

    /** Deletes the account from the system (won't touch collections on the server). */
    fun deleteAccount() {
        notInterruptibleScope.launch {
            accountRepository.delete(account.name)
        }
    }

    fun refreshCollections() {
        cardDavSvc.value?.let { svc ->
            RefreshCollectionsWorker.enqueue(context, svc.id)
        }
        calDavSvc.value?.let { svc ->
            RefreshCollectionsWorker.enqueue(context, svc.id)
        }
    }

    /**
     * Renames the [account] to given name.
     *
     * @param newName new account name
     */
    fun renameAccount(newName: String) {
        notInterruptibleScope.launch {
            try {
                accountRepository.rename(account.name, newName)

                // synchronize again
                val newAccount = Account(context.getString(R.string.account_type), newName)
                OneTimeSyncWorker.enqueueAllAuthorities(context, newAccount, manual = true)
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Couldn't rename account", e)
                error = e.localizedMessage
            }
        }
    }

    fun setCollectionSync(id: Long, sync: Boolean) {
        viewModelScope.launch {
            collectionRepository.setSync(id, sync)
        }
    }

    fun sync() {
        OneTimeSyncWorker.enqueueAllAuthorities(context, account, manual = true)
    }

}