/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import androidx.viewpager2.adapter.FragmentStateAdapter
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.ActivityAccountBinding
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.syncadapter.SyncWorker
import at.bitfire.davdroid.ui.AppWarningsManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.logging.Level
import javax.inject.Inject

@AndroidEntryPoint
class AccountActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    @Inject lateinit var modelFactory: Model.Factory
    val model by viewModels<Model> {
        val account = intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account
                ?: throw IllegalArgumentException("AccountActivity requires EXTRA_ACCOUNT")
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T: ViewModel> create(modelClass: Class<T>) =
                modelFactory.create(account) as T
        }
    }

    private lateinit var binding: ActivityAccountBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = model.account.name

        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        model.accountExists.observe(this) { accountExists ->
            if (!accountExists)
                finish()
        }

        model.services.observe(this) { services ->
            val calDavServiceId = services.firstOrNull { it.type == Service.TYPE_CALDAV }?.id
            val cardDavServiceId = services.firstOrNull { it.type == Service.TYPE_CARDDAV }?.id

            val viewPager = binding.viewPager
            val adapter = FragmentsAdapter(this, cardDavServiceId, calDavServiceId)
            viewPager.adapter = adapter

            // connect ViewPager with TabLayout (top bar with tabs)
            TabLayoutMediator(binding.tabLayout, viewPager) { tab, position ->
                tab.text = adapter.getHeading(position)
            }.attach()
        }

        // "Sync now" fab
        TooltipCompat.setTooltipText(binding.sync, binding.sync.contentDescription)
        model.networkAvailable.observe(this) { networkAvailable ->
            binding.sync.setOnClickListener {
                if (!networkAvailable)
                    Snackbar.make(
                        binding.sync,
                        R.string.no_internet_sync_scheduled,
                        Snackbar.LENGTH_LONG
                    ).show()
                SyncWorker.enqueueAllAuthorities(this, model.account)
            }
        }

        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.activity_account, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem) =
                when (menuItem.itemId) {
                    R.id.settings -> {
                        openAccountSettings()
                        true
                    }
                    R.id.rename_account -> {
                        renameAccount()
                        true
                    }
                    R.id.delete_account -> {
                        deleteAccountDialog()
                        true
                    }
                    else -> false
                }
        })
    }


    // menu actions

    fun openAccountSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        intent.putExtra(SettingsActivity.EXTRA_ACCOUNT, model.account)
        startActivity(intent, null)
    }

    fun renameAccount() {
        RenameAccountFragment.newInstance(model.account).show(supportFragmentManager, null)
    }

    fun deleteAccountDialog() {
        MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_error)
                .setTitle(R.string.account_delete_confirmation_title)
                .setMessage(R.string.account_delete_confirmation_text)
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    deleteAccount()
                }
                .show()
    }

    private fun deleteAccount() {
        val accountManager = AccountManager.get(this)

        accountManager.removeAccount(model.account, this, { future ->
            try {
                if (future.result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT))
                    Handler(Looper.getMainLooper()).post {
                        finish()
                    }
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't remove account", e)
            }
        }, null)
    }


    // public functions

    /**
     * Updates the click listener of the refresh collections list FAB, according to the given
     * fragment. Should be called when the related fragment is resumed.
     */
    fun updateRefreshCollectionsListAction(fragment: CollectionsFragment) {
        val label = when (fragment) {
            is AddressBooksFragment ->
                getString(R.string.account_refresh_address_book_list)

            is CalendarsFragment,
            is WebcalFragment ->
                getString(R.string.account_refresh_calendar_list)

            else -> null
        }
        if (label != null) {
            binding.refresh.contentDescription = label
            TooltipCompat.setTooltipText(binding.refresh, label)
        }

        binding.refresh.setOnClickListener {
            Snackbar.make(binding.refresh, R.string.refresh_requested, Snackbar.LENGTH_LONG).show()
            fragment.model.refresh()
        }
    }



    // adapter

    class FragmentsAdapter(
        val activity: FragmentActivity,
        private val cardDavSvcId: Long?,
        private val calDavSvcId: Long?
    ): FragmentStateAdapter(activity) {

        private val idxCardDav: Int?
        private val idxCalDav: Int?
        private val idxWebcal: Int?

        init {
            var currentIndex = 0

            idxCardDav = if (cardDavSvcId != null)
                currentIndex++
            else
                null

            if (calDavSvcId != null) {
                idxCalDav = currentIndex++
                idxWebcal = currentIndex
            } else {
                idxCalDav = null
                idxWebcal = null
            }
        }

        override fun getItemCount() =
            (if (idxCardDav != null) 1 else 0) +
            (if (idxCalDav != null) 1 else 0) +
            (if (idxWebcal != null) 1 else 0)

        override fun createFragment(position: Int) =
            when (position) {
                idxCardDav ->
                    AddressBooksFragment().apply {
                        arguments = Bundle(2).apply {
                            putLong(CollectionsFragment.EXTRA_SERVICE_ID, cardDavSvcId!!)
                            putString(CollectionsFragment.EXTRA_COLLECTION_TYPE, Collection.TYPE_ADDRESSBOOK)
                        }
                    }
                idxCalDav ->
                    CalendarsFragment().apply {
                        arguments = Bundle(2).apply {
                            putLong(CollectionsFragment.EXTRA_SERVICE_ID, calDavSvcId!!)
                            putString(CollectionsFragment.EXTRA_COLLECTION_TYPE, Collection.TYPE_CALENDAR)
                        }
                    }
                idxWebcal ->
                    WebcalFragment().apply {
                        arguments = Bundle(2).apply {
                            putLong(CollectionsFragment.EXTRA_SERVICE_ID, calDavSvcId!!)
                            putString(CollectionsFragment.EXTRA_COLLECTION_TYPE, Collection.TYPE_WEBCAL)
                        }
                    }
                else -> throw IllegalArgumentException()
            }

        fun getHeading(position: Int) =
            when (position) {
                idxCardDav -> activity.getString(R.string.account_carddav)
                idxCalDav -> activity.getString(R.string.account_caldav)
                idxWebcal -> activity.getString(R.string.account_webcal)
                else -> throw IllegalArgumentException()
            }

    }


    // model

    class Model @AssistedInject constructor(
        application: Application,
        val db: AppDatabase,
        @Assisted val account: Account,
        warnings: AppWarningsManager
    ): AndroidViewModel(application), OnAccountsUpdateListener {

        @AssistedFactory
        interface Factory {
            fun create(account: Account): Model
        }

        val accountManager: AccountManager = AccountManager.get(application)
        val accountSettings by lazy { AccountSettings(application, account) }

        val accountExists = MutableLiveData<Boolean>()
        val services = db.serviceDao().getServiceTypeAndIdsByAccount(account.name)

        val showOnlyPersonal = MutableLiveData<Boolean>()
        val showOnlyPersonalWritable = MutableLiveData<Boolean>()

        val networkAvailable = warnings.networkAvailable


        init {
            accountManager.addOnAccountsUpdatedListener(this, null, true)
            viewModelScope.launch(Dispatchers.IO) {
                accountSettings.getShowOnlyPersonal().let { (value, locked) ->
                    showOnlyPersonal.postValue(value)
                    showOnlyPersonalWritable.postValue(locked)
                }
            }
        }

        override fun onCleared() {
            accountManager.removeOnAccountsUpdatedListener(this)
        }

        override fun onAccountsUpdated(accounts: Array<out Account>) {
            accountExists.postValue(accounts.contains(account))
        }

        fun toggleReadOnly(item: Collection) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                val newItem = item.copy(forceReadOnly = !item.forceReadOnly)
                db.collectionDao().update(newItem)
            }
        }

        fun toggleShowOnlyPersonal() {
            showOnlyPersonal.value?.let { oldValue ->
                val newValue = !oldValue
                accountSettings.setShowOnlyPersonal(newValue)
                showOnlyPersonal.postValue(newValue)
            }
        }

        fun toggleSync(item: Collection) {
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                val newItem = item.copy(sync = !item.sync)
                db.collectionDao().update(newItem)
            }
        }

    }

}