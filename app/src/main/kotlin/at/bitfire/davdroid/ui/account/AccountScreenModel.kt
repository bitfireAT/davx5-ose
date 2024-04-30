/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Application
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import at.bitfire.davdroid.util.TaskUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.logging.Level

class AccountScreenModel @AssistedInject constructor(
    val context: Application,
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
        fun create(account: Account): AccountScreenModel
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

    private val cardDavSvc = serviceRepository
        .getCardDavServiceFlow(account.name)
        .stateIn(viewModelScope, initialValue = null, started = SharingStarted.Eagerly)
    val hasCardDav = cardDavSvc.map { it != null }
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

    private val calDavSvc = serviceRepository
        .getCalDavServiceFlow(account.name)
        .stateIn(viewModelScope, initialValue = null, started = SharingStarted.Eagerly)
    val hasCalDav = calDavSvc.map { it != null }
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
    val hasWebcal = calDavSvc.map { service ->
        if (service != null)
            collectionRepository.anyWebcal(service.id)
        else
            false
    }
    val webcalPager = getServiceCollectionPagerUseCase(calDavSvc, Collection.TYPE_WEBCAL, showOnlyPersonal)


    var error by mutableStateOf<String?>(null)
        private set

    fun resetError() { error = null }


    var showNoWebcalApp by mutableStateOf(false)
        private set

    fun noWebcalApp() { showNoWebcalApp = true }
    fun resetShowNoWebcalApp() { showNoWebcalApp = false }


    // actions

    /** Deletes the account from the system (won't touch collections on the server). */
    fun deleteAccount() {
        viewModelScope.launch {
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

    fun setCollectionSync(id: Long, sync: Boolean) {
        viewModelScope.launch {
            collectionRepository.setSync(id, sync)
        }
    }

    fun sync() {
        OneTimeSyncWorker.enqueueAllAuthorities(context, account, manual = true)
    }

}