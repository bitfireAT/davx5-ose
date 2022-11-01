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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.*
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.DavUtils.SyncStatus
import at.bitfire.davdroid.PermissionUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.StorageLowReceiver
import at.bitfire.davdroid.databinding.AccountListBinding
import at.bitfire.davdroid.databinding.AccountListItemBinding
import at.bitfire.davdroid.ui.account.AccountActivity
import dagger.hilt.android.AndroidEntryPoint
import java.text.Collator
import javax.inject.Inject

@AndroidEntryPoint
class AccountListFragment: Fragment() {

    @Inject lateinit var storageLowReceiver: StorageLowReceiver

    private var _binding: AccountListBinding? = null
    private val binding get() = _binding!!
    val model by viewModels<Model>()

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

        model.networkAvailable.observe(viewLifecycleOwner) { networkAvailable ->
            binding.noNetworkInfo.visibility = if (networkAvailable) View.GONE else View.VISIBLE
        }
        binding.manageConnections.setOnClickListener {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            if (intent.resolveActivity(requireActivity().packageManager) != null)
                startActivity(intent)
        }

        storageLowReceiver.storageLow.observe(viewLifecycleOwner) { storageLow ->
            binding.lowStorageInfo.visibility = if (storageLow) View.VISIBLE else View.GONE
        }
        binding.manageStorage.setOnClickListener {
            val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            if (intent.resolveActivity(requireActivity().packageManager) != null)
                startActivity(intent)
        }

        val accountAdapter = AccountAdapter(requireActivity())
        binding.list.apply {
            layoutManager = LinearLayoutManager(requireActivity())
            adapter = accountAdapter
        }
        model.accounts.observe(viewLifecycleOwner, { accounts ->
            if (accounts.isEmpty()) {
                binding.list.visibility = View.GONE
                binding.empty.visibility = View.VISIBLE
            } else {
                binding.list.visibility = View.VISIBLE
                binding.empty.visibility = View.GONE
            }
            accountAdapter.submitList(accounts)
            requireActivity().invalidateOptionsMenu()
        })
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


    class Model(
            application: Application
    ): AndroidViewModel(application), OnAccountsUpdateListener, SyncStatusObserver {

        data class AccountInfo(
                val account: Account,
                val status: SyncStatus
        )

        val accounts = MutableLiveData<List<AccountInfo>>()
        val syncAuthorities by lazy { DavUtils.syncAuthorities(getApplication()) }

        val networkAvailable = MutableLiveData<Boolean>()
        private var networkCallback: ConnectivityManager.NetworkCallback? = null
        private var networkReceiver: BroadcastReceiver? = null

        private val accountManager = AccountManager.get(getApplication())!!
        private val connectivityManager = application.getSystemService<ConnectivityManager>()!!
        init {
            // watch accounts
            accountManager.addOnAccountsUpdatedListener(this, null, true)

            // watch account status
            ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE or ContentResolver.SYNC_OBSERVER_TYPE_PENDING, this)

            // watch connectivity
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {    // API level <26
                networkReceiver = object: BroadcastReceiver() {
                    init {
                        update()
                    }

                    override fun onReceive(context: Context?, intent: Intent?) = update()

                    private fun update() {
                        networkAvailable.postValue(connectivityManager.allNetworkInfo.any { it.isConnected })
                    }
                }
                application.registerReceiver(networkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

            } else {    // API level >= 26
                networkAvailable.postValue(false)

                // check for working (e.g. WiFi after captive portal login) Internet connection
                val networkRequest = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        .build()
                val callback = object: ConnectivityManager.NetworkCallback() {
                    val availableNetworks = hashSetOf<Network>()

                    override fun onAvailable(network: Network) {
                        availableNetworks += network
                        update()
                    }

                    override fun onLost(network: Network) {
                        availableNetworks -= network
                        update()
                    }

                    private fun update() {
                        networkAvailable.postValue(availableNetworks.isNotEmpty())
                    }
                }
                connectivityManager.registerNetworkCallback(networkRequest, callback)
                networkCallback = callback
            }
        }

        override fun onCleared() {
            accountManager.removeOnAccountsUpdatedListener(this)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                networkReceiver?.let {
                    getApplication<Application>().unregisterReceiver(it)
                }

            else
                networkCallback?.let {
                    connectivityManager.unregisterNetworkCallback(it)
                }
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
                AccountInfo(account, DavUtils.accountSyncStatus(context, syncAuthorities, account))
            }
            accounts.postValue(accountsWithInfo)
        }

    }

}