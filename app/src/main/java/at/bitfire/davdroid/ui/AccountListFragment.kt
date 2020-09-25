/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Activity
import android.app.Application
import android.content.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.view.*
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
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.account.AccountActivity
import kotlinx.android.synthetic.main.account_list.*
import kotlinx.android.synthetic.main.account_list_item.view.*
import java.text.Collator

class AccountListFragment: Fragment() {

    val model by viewModels<Model>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.account_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model.networkAvailable.observe(viewLifecycleOwner, { networkAvailable ->
            no_network_info.visibility = if (networkAvailable) View.GONE else View.VISIBLE
        })

        val accountAdapter = AccountAdapter(requireActivity())
        list.apply {
            layoutManager = LinearLayoutManager(requireActivity())
            adapter = accountAdapter
        }
        model.accounts.observe(viewLifecycleOwner, { accounts ->
            if (accounts.isEmpty()) {
                list.visibility = View.GONE
                empty.visibility = View.VISIBLE
            } else {
                list.visibility = View.VISIBLE
                empty.visibility = View.GONE
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
        class ViewHolder(val v: View): RecyclerView.ViewHolder(v)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.account_list_item, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val v = holder.v
            val accountInfo = currentList[position]

            v.setOnClickListener {
                val intent = Intent(activity, AccountActivity::class.java)
                intent.putExtra(AccountActivity.EXTRA_ACCOUNT, accountInfo.account)
                activity.startActivity(intent)
            }

            when (accountInfo.status) {
                SyncStatus.ACTIVE -> {
                    v.progress.apply {
                        alpha = 1.0f
                        isIndeterminate = true
                        visibility = View.VISIBLE
                    }
                }
                SyncStatus.PENDING -> {
                    v.progress.apply {
                        alpha = 0.4f
                        isIndeterminate = false
                        progress = 100
                        visibility = View.VISIBLE
                    }
                }
                else -> v.progress.visibility = View.INVISIBLE
            }
            v.account_name.text = accountInfo.account.name
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
                    .sortedArrayWith({ a, b ->
                        collator.compare(a.name, b.name)
                    })
            val accountsWithInfo = sortedAccounts.map { account ->
                AccountInfo(account, DavUtils.accountSyncStatus(context, syncAuthorities, account))
            }
            accounts.postValue(accountsWithInfo)
        }

    }

}