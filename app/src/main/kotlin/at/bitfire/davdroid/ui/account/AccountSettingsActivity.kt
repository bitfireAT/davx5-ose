/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import at.bitfire.davdroid.syncadapter.Syncer
import at.bitfire.davdroid.util.TaskUtils
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AccountSettingsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"

        const val ACCOUNT_SETTINGS_HELP_URL = "https://manual.davx5.com/settings.html#account-settings"
    }

    private val account by lazy {
        intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
    }

    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model> {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) =
                modelFactory.create(account) as T
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = account.name

        setContent {
            AccountSettingsScreen(
                onNavUp = { onSupportNavigateUp() },
                onSyncWifiOnlyPermissionsAction = {
                    val intent = Intent(this, WifiPermissionsActivity::class.java)
                    intent.putExtra(WifiPermissionsActivity.EXTRA_ACCOUNT, account)
                    startActivity(intent)
                },
                model,
                account
            )
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
    }

    class Model @AssistedInject constructor(
        val context: Application,
        val settings: SettingsManager,
        @Assisted val account: Account
    ): ViewModel(), SettingsManager.OnChangeListener {

        @AssistedFactory
        interface Factory {
            fun create(account: Account): Model
        }

        private var accountSettings: AccountSettings? = null

        // settings
        val syncIntervalContacts = MutableLiveData<Long>()
        val syncIntervalCalendars = MutableLiveData<Long>()

        val tasksProvider = TaskUtils.currentProvider(context)
        val syncIntervalTasks = MutableLiveData<Long>()

        val syncWifiOnly = MutableLiveData<Boolean>()
        val syncWifiOnlySSIDs = MutableLiveData<List<String>>()
        val ignoreVpns = MutableLiveData<Boolean>()

        val credentials = MutableLiveData<Credentials>()

        val timeRangePastDays = MutableLiveData<Int>()
        val defaultAlarmMinBefore = MutableLiveData<Int>()
        val manageCalendarColors = MutableLiveData<Boolean>()
        val eventColors = MutableLiveData<Boolean>()

        val contactGroupMethod = MutableLiveData<GroupMethod>()


        init {
            accountSettings = AccountSettings(context, account)

            settings.addOnChangeListener(this)

            reload()
        }

        override fun onCleared() {
            super.onCleared()
            settings.removeOnChangeListener(this)
        }

        override fun onSettingsChanged() {
            Logger.log.info("Settings changed")
            reload()
        }

        fun reload() {
            val accountSettings = accountSettings ?: return

            syncIntervalContacts.postValue(
                accountSettings.getSyncInterval(context.getString(R.string.address_books_authority))
            )
            syncIntervalCalendars.postValue(accountSettings.getSyncInterval(CalendarContract.AUTHORITY))
            syncIntervalTasks.postValue(tasksProvider?.let { accountSettings.getSyncInterval(it.authority) })

            syncWifiOnly.postValue(accountSettings.getSyncWifiOnly())
            syncWifiOnlySSIDs.postValue(accountSettings.getSyncWifiOnlySSIDs())
            ignoreVpns.postValue(accountSettings.getIgnoreVpns())

            credentials.postValue(accountSettings.credentials())

            timeRangePastDays.postValue(accountSettings.getTimeRangePastDays())
            defaultAlarmMinBefore.postValue(accountSettings.getDefaultAlarm())
            manageCalendarColors.postValue(accountSettings.getManageCalendarColors())
            eventColors.postValue(accountSettings.getEventColors())

            contactGroupMethod.postValue(accountSettings.getGroupMethod())
        }


        fun updateSyncInterval(authority: String, syncInterval: Long) {
            CoroutineScope(Dispatchers.Default).launch {
                accountSettings?.setSyncInterval(authority, syncInterval)
                reload()
            }
        }

        fun updateSyncWifiOnly(wifiOnly: Boolean) {
            accountSettings?.setSyncWiFiOnly(wifiOnly)
            reload()
        }

        fun updateSyncWifiOnlySSIDs(ssids: List<String>?) {
            accountSettings?.setSyncWifiOnlySSIDs(ssids)
            reload()
        }

        fun updateIgnoreVpns(ignoreVpns: Boolean) {
            accountSettings?.setIgnoreVpns(ignoreVpns)
            reload()
        }

        fun updateCredentials(credentials: Credentials) {
            accountSettings?.credentials(credentials)
            reload()
        }

        fun updateTimeRangePastDays(days: Int?) {
            accountSettings?.setTimeRangePastDays(days)
            reload()

            /* If the new setting is a certain number of days, no full resync is required,
            because every sync will cause a REPORT calendar-query with the given number of days.
            However, if the new setting is "all events", collection sync may/should be used, so
            the last sync-token has to be reset, which is done by setting fullResync=true.
             */
            resyncCalendars(fullResync = days == null, tasks = false)
        }

        fun updateDefaultAlarm(minBefore: Int?) {
            accountSettings?.setDefaultAlarm(minBefore)
            reload()

            resyncCalendars(fullResync = true, tasks = false)
        }

        fun updateManageCalendarColors(manage: Boolean) {
            accountSettings?.setManageCalendarColors(manage)
            reload()

            resyncCalendars(fullResync = false, tasks = true)
        }

        fun updateEventColors(manageColors: Boolean) {
            accountSettings?.setEventColors(manageColors)
            reload()

            resyncCalendars(fullResync = true, tasks = false)
        }

        fun updateContactGroupMethod(groupMethod: GroupMethod) {
            accountSettings?.setGroupMethod(groupMethod)
            reload()

            resync(
                authority = context.getString(R.string.address_books_authority),
                fullResync = true
            )
        }

        /**
         * Initiates calendar re-synchronization.
         *
         * @param fullResync whether sync shall download all events again
         * (_true_: sets [Syncer.SYNC_EXTRAS_FULL_RESYNC],
         * _false_: sets [Syncer.SYNC_EXTRAS_RESYNC])
         * @param tasks whether tasks shall be synchronized, too (false: only events, true: events and tasks)
         */
        private fun resyncCalendars(fullResync: Boolean, tasks: Boolean) {
            resync(CalendarContract.AUTHORITY, fullResync)
            if (tasks)
                resync(TaskProvider.ProviderName.OpenTasks.authority, fullResync)
        }

        /**
         * Initiates re-synchronization for given authority.
         *
         * @param authority authority to re-sync
         * @param fullResync whether sync shall download all events again
         * (_true_: sets [Syncer.SYNC_EXTRAS_FULL_RESYNC],
         * _false_: sets [Syncer.SYNC_EXTRAS_RESYNC])
         */
        private fun resync(authority: String, fullResync: Boolean) {
            val resync =
                if (fullResync)
                    OneTimeSyncWorker.FULL_RESYNC
                else
                    OneTimeSyncWorker.RESYNC
            OneTimeSyncWorker.enqueue(context, account, authority, resync = resync)
        }

    }

}