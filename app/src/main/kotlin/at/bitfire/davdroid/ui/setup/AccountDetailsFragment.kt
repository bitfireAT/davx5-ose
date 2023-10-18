/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.LoginAccountDetailsBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.syncadapter.AccountUtils
import at.bitfire.davdroid.ui.account.AccountActivity
import at.bitfire.davdroid.util.context
import at.bitfire.vcard4android.GroupMethod
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.util.logging.Level
import javax.inject.Inject

@AndroidEntryPoint
class AccountDetailsFragment : Fragment() {

    @Inject lateinit var settings: SettingsManager

    val loginModel by activityViewModels<LoginModel>()
    val model by viewModels<Model>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = LoginAccountDetailsBinding.inflate(inflater, container, false)
        v.lifecycleOwner = viewLifecycleOwner
        v.details = model

        val config = loginModel.configuration ?: throw IllegalStateException()

        // default account name
        model.name.value =
                config.calDAV?.emails?.firstOrNull()
                        ?: loginModel.suggestedAccountName
                        ?: loginModel.credentials?.userName
                        ?: loginModel.credentials?.certificateAlias
                        ?: loginModel.baseURI?.host

        // CardDAV-specific
        v.carddav.visibility = if (config.cardDAV != null) View.VISIBLE else View.GONE
        if (settings.containsKey(AccountSettings.KEY_CONTACT_GROUP_METHOD))
            v.contactGroupMethod.isEnabled = false

        // CalDAV-specific
        config.calDAV?.let {
            val accountNameAdapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, it.emails)
            v.accountName.setAdapter(accountNameAdapter)
        }

        v.createAccount.setOnClickListener {
            val name = model.name.value
            if (name.isNullOrBlank())
                model.nameError.value = getString(R.string.login_account_name_required)
            else {
                // check whether account name already exists
                val am = AccountManager.get(requireActivity())
                if (am.getAccountsByType(getString(R.string.account_type)).any { it.name == name }) {
                    model.nameError.value = getString(R.string.login_account_name_already_taken)
                    return@setOnClickListener
                }

                val idx = v.contactGroupMethod.selectedItemPosition
                val groupMethodName = resources.getStringArray(R.array.settings_contact_group_method_values)[idx]

                v.createAccountProgress.visibility = View.VISIBLE
                v.createAccount.visibility = View.GONE

                model.createAccount(
                    name,
                    loginModel.credentials,
                    config,
                    GroupMethod.valueOf(groupMethodName)
                ).observe(viewLifecycleOwner, Observer { success ->
                    if (success) {
                        // close Create account activity
                        requireActivity().finish()
                        // open Account activity for created account
                        val intent = Intent(requireActivity(), AccountActivity::class.java)
                        val account = Account(name, getString(R.string.account_type))
                        intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
                        startActivity(intent)
                    } else {
                        Snackbar.make(requireActivity().findViewById(android.R.id.content), R.string.login_account_not_created, Snackbar.LENGTH_LONG).show()

                        v.createAccountProgress.visibility = View.GONE
                        v.createAccount.visibility = View.VISIBLE
                    }
                })
            }
        }

        val forcedGroupMethod = settings.getString(AccountSettings.KEY_CONTACT_GROUP_METHOD)?.let { GroupMethod.valueOf(it) }
        if (forcedGroupMethod != null) {
            // contact group type forced by settings
            v.contactGroupMethod.isEnabled = false
            for ((i, method) in resources.getStringArray(R.array.settings_contact_group_method_values).withIndex()) {
                if (method == forcedGroupMethod.name) {
                    v.contactGroupMethod.setSelection(i)
                    break
                }
            }
        } else {
            // contact group type selectable
            v.contactGroupMethod.isEnabled = true
            for ((i, method) in resources.getStringArray(R.array.settings_contact_group_method_values).withIndex()) {
                // take suggestion from detection process into account
                if (method == loginModel.suggestedGroupMethod.name) {
                    v.contactGroupMethod.setSelection(i)
                    break
                }
            }
        }

        return v.root
    }


    class Model(
        application: Application,
        val db: AppDatabase,
        val settingsManager: SettingsManager
    ) : AndroidViewModel(application) {

        val name = MutableLiveData<String>()
        val nameError = MutableLiveData<String>()
        val showApostropheWarning = MutableLiveData<Boolean>(false)

        fun validateAccountName(s: Editable) {
            showApostropheWarning.value = s.toString().contains('\'')
            nameError.value = null
        }

        /**
         * Creates a new main account with discovered services and enables periodic syncs with
         * default sync interval times.
         *
         * @param name Name of the account
         * @param credentials Server credentials
         * @param config Discovered server capabilities for syncable authorities
         * @param groupMethod Whether CardDAV contact groups are separate VCards or as contact categories
         * @return *true* if account creation was succesful; *false* otherwise (for instance because an account with this name already exists)
         */
        fun createAccount(name: String, credentials: Credentials?, config: DavResourceFinder.Configuration, groupMethod: GroupMethod): LiveData<Boolean> {
            val result = MutableLiveData<Boolean>()
            viewModelScope.launch(Dispatchers.Default + NonCancellable) {
                val account = Account(name, context.getString(R.string.account_type))

                // create Android account
                val userData = AccountSettings.initialUserData(credentials)
                Logger.log.log(Level.INFO, "Creating Android account with initial config", arrayOf(account, userData))

                if (!AccountUtils.createAccount(context, account, userData, credentials?.password)) {
                    result.postValue(false)
                    return@launch
                }

                // add entries for account to service DB
                Logger.log.log(Level.INFO, "Writing account configuration to database", config)
                try {
                    val accountSettings = AccountSettings(context, account)
                    val defaultSyncInterval = settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL)

                    // Configure CardDAV service
                    val addrBookAuthority = context.getString(R.string.address_books_authority)
                    if (config.cardDAV != null) {
                        // insert CardDAV service
                        val id = insertService(name, Service.TYPE_CARDDAV, config.cardDAV)

                        // initial CardDAV account settings
                        accountSettings.setGroupMethod(groupMethod)

                        // start CardDAV service detection (refresh collections)
                        RefreshCollectionsWorker.refreshCollections(context, id)

                        // set default sync interval and enable sync regardless of permissions
                        ContentResolver.setIsSyncable(account, addrBookAuthority, 1)
                        accountSettings.setSyncInterval(addrBookAuthority, defaultSyncInterval)
                    } else
                        ContentResolver.setIsSyncable(account, addrBookAuthority, 0)

                    // Configure CalDAV service
                    if (config.calDAV != null) {
                        // insert CalDAV service
                        val id = insertService(name, Service.TYPE_CALDAV, config.calDAV)

                        // start CalDAV service detection (refresh collections)
                        RefreshCollectionsWorker.refreshCollections(context, id)

                        // set default sync interval and enable sync regardless of permissions
                        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
                        accountSettings.setSyncInterval(CalendarContract.AUTHORITY, defaultSyncInterval)

                        // if task provider present, set task sync interval and enable sync
                        val taskProvider = TaskUtils.currentProvider(context)
                        if (taskProvider != null) {
                            ContentResolver.setIsSyncable(account, taskProvider.authority, 1)
                            accountSettings.setSyncInterval(taskProvider.authority, defaultSyncInterval)
                            // further changes will be handled by TasksWatcher on app start or when tasks app is (un)installed
                            Logger.log.info("Tasks provider ${taskProvider.authority} found. Tasks sync enabled.")
                        } else
                            Logger.log.info("No tasks provider found. Did not enable tasks sync.")
                    } else
                        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0)

                } catch(e: InvalidAccountException) {
                    Logger.log.log(Level.SEVERE, "Couldn't access account settings", e)
                    result.postValue(false)
                    return@launch
                }
                result.postValue(true)
            }
            return result
        }

        private fun insertService(accountName: String, type: String, info: DavResourceFinder.Configuration.ServiceInfo): Long {
            // insert service
            val service = Service(0, accountName, type, info.principal)
            val serviceId = db.serviceDao().insertOrReplace(service)

            // insert home sets
            val homeSetDao = db.homeSetDao()
            for (homeSet in info.homeSets)
                homeSetDao.insertOrUpdateByUrl(HomeSet(0, serviceId, true, homeSet))

            // insert collections
            val collectionDao = db.collectionDao()
            for (collection in info.collections.values) {
                collection.serviceId = serviceId
                collectionDao.insertOrUpdateByUrl(collection)
            }

            return serviceId
        }

    }

}