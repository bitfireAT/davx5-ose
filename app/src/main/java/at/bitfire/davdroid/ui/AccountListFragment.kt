/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Activity
import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.net.*
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.davdroid.util.DavUtils.SyncStatus
import at.bitfire.davdroid.R
import at.bitfire.davdroid.databinding.AccountListBinding
import at.bitfire.davdroid.databinding.AccountListItemBinding
import at.bitfire.davdroid.ui.account.AccountActivity
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Collator
import javax.inject.Inject

@AndroidEntryPoint
class AccountListFragment: Fragment() {

    private var _binding: AccountListBinding? = null
    private val binding get() = _binding!!
    val model by viewModels<Model>()

    private var syncStatusSnackbar: Snackbar? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setHasOptionsMenu(true)

        _binding = AccountListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.allowNotifications.setOnClickListener {
            startActivity(Intent(requireActivity(), PermissionsActivity::class.java))
        }

        model.globalSyncDisabled.observe(viewLifecycleOwner) { syncDisabled ->
            if (syncDisabled) {
                val snackbar = Snackbar
                    .make(view, R.string.accounts_global_sync_disabled, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.accounts_global_sync_enable) {
                        ContentResolver.setMasterSyncAutomatically(true)
                    }
                snackbar.show()
                syncStatusSnackbar = snackbar
            } else {
                syncStatusSnackbar?.let { snackbar ->
                    snackbar.dismiss()
                    syncStatusSnackbar = null
                }
            }
        }

        model.networkAvailable.observe(viewLifecycleOwner) { networkAvailable ->
            binding.noNetworkInfo.visibility = if (networkAvailable) View.GONE else View.VISIBLE
        }
        binding.manageConnections.setOnClickListener {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            if (intent.resolveActivity(requireActivity().packageManager) != null)
                startActivity(intent)
        }

        model.storageLow.observe(viewLifecycleOwner) { storageLow ->
            binding.lowStorageInfo.visibility = if (storageLow) View.VISIBLE else View.GONE
        }
        binding.manageStorage.setOnClickListener {
            val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            if (intent.resolveActivity(requireActivity().packageManager) != null)
                startActivity(intent)
        }

        model.dataSaverOn.observe(viewLifecycleOwner) { datasaverOn ->
            binding.datasaverOnInfo.visibility = if (Build.VERSION.SDK_INT >= 24 && datasaverOn) View.VISIBLE else View.GONE
        }
        binding.manageDatasaver.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 24) {
                val intent = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:" + requireActivity().packageName))
                if (intent.resolveActivity(requireActivity().packageManager) != null)
                    startActivity(intent)
            }
        }

        val accountAdapter = AccountAdapter(requireActivity())
        binding.list.apply {
            layoutManager = LinearLayoutManager(requireActivity())
            adapter = accountAdapter
        }
        model.accounts.observe(viewLifecycleOwner) { accounts ->
            if (accounts.isEmpty()) {
                binding.list.visibility = View.GONE
                binding.empty.visibility = View.VISIBLE
            } else {
                binding.list.visibility = View.VISIBLE
                binding.empty.visibility = View.GONE
            }
            accountAdapter.submitList(accounts)
            requireActivity().invalidateOptionsMenu()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) =
        inflater.inflate(R.menu.activity_accounts, menu)

    override fun onPrepareOptionsMenu(menu: Menu) {
        // Show "Sync all" only when there is at least one account
        model.accounts.value?.let { accounts ->
            menu.findItem(R.id.syncAll).setVisible(accounts.isNotEmpty())
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            binding.noNotificationsInfo.visibility = View.GONE
        else
            binding.noNotificationsInfo.visibility = View.VISIBLE
    }


    class AccountAdapter(
            val activity: Activity
    ): ListAdapter<Model.AccountInfo, AccountAdapter.ViewHolder>(
            object: DiffUtil.ItemCallback<Model.AccountInfo>() {
                override fun areItemsTheSame(oldItem: Model.AccountInfo, newItem: Model.AccountInfo) =
                        oldItem.account == newItem.account
                override fun areContentsTheSame(oldItem: Model.AccountInfo, newItem: Model.AccountInfo) =
                        oldItem == newItem
            }
    ) {

        class ViewHolder(val binding: AccountListItemBinding): RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = AccountListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val accountInfo = currentList[position]

            holder.binding.root.setOnClickListener {
                val intent = Intent(activity, AccountActivity::class.java)
                intent.putExtra(AccountActivity.EXTRA_ACCOUNT, accountInfo.account)
                activity.startActivity(intent)
            }

            when (accountInfo.status) {
                SyncStatus.ACTIVE -> {
                    holder.binding.progress.apply {
                        alpha = 1.0f
                        isIndeterminate = true
                        visibility = View.VISIBLE
                    }
                }
                SyncStatus.PENDING -> {
                    holder.binding.progress.apply {
                        alpha = 0.4f
                        isIndeterminate = false
                        progress = 100
                        visibility = View.VISIBLE
                    }
                }
                else -> holder.binding.progress.visibility = View.INVISIBLE
            }
            holder.binding.accountName.text = accountInfo.account.name
        }
    }


    @HiltViewModel
    class Model @Inject constructor(
        application: Application,
        private val warnings: AppWarningsManager
    ): AndroidViewModel(application), OnAccountsUpdateListener, SyncStatusObserver {

        data class AccountInfo(
            val account: Account,
            val status: SyncStatus
        )

        // Warnings
        val globalSyncDisabled = warnings.globalSyncDisabled
        val dataSaverOn = warnings.dataSaverEnabled
        val networkAvailable = warnings.networkAvailable
        val storageLow = warnings.storageLow

        // Accounts
        val accounts = MutableLiveData<List<AccountInfo>>()
        private val accountManager = AccountManager.get(application)!!
        private val syncAuthorities by lazy { DavUtils.syncAuthorities(application) }

        init {
            // watch accounts
            accountManager.addOnAccountsUpdatedListener(this, null, true)

            // watch account status
            ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE or ContentResolver.SYNC_OBSERVER_TYPE_PENDING,
                this
            )
        }

        override fun onAccountsUpdated(newAccounts: Array<out Account>) {
            reloadAccounts()
        }

        override fun onStatusChanged(which: Int) {
            reloadAccounts()
        }

        private fun reloadAccounts() {
            val context = getApplication<Application>()
            val collator = Collator.getInstance()

            val sortedAccounts = accountManager
                .getAccountsByType(context.getString(R.string.account_type))
                .sortedArrayWith { a, b ->
                    collator.compare(a.name, b.name)
                }
            val accountsWithInfo = sortedAccounts.map { account ->
                AccountInfo(
                    account,
                    DavUtils.accountSyncStatus(context, syncAuthorities, account)
                )
            }
            accounts.postValue(accountsWithInfo)
        }

        override fun onCleared() {
            accountManager.removeOnAccountsUpdatedListener(this)
            warnings.close()
        }

    }

}