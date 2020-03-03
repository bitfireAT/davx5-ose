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
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.ListFragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.account.AccountActivity
import kotlinx.android.synthetic.main.account_list.*
import kotlinx.android.synthetic.main.account_list_item.view.*

class AccountListFragment: ListFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        listAdapter = AccountListAdapter(requireActivity())

        val model = ViewModelProvider(this).get(Model::class.java)
        model.accounts.observe(viewLifecycleOwner, Observer { accounts ->
            val adapter = listAdapter as AccountListAdapter
            adapter.clear()
            adapter.addAll(*accounts)
        })

        model.networkAvailable.observe(viewLifecycleOwner, Observer { networkAvailable ->
            no_network_info.visibility = if (networkAvailable) View.GONE else View.VISIBLE
        })

        return inflater.inflate(R.layout.account_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val account = listAdapter!!.getItem(position) as Account
            val intent = Intent(activity, AccountActivity::class.java)
            intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
            startActivity(intent)
        }
    }


    // list adapter

    class AccountListAdapter(
            context: Context
    ): ArrayAdapter<Account>(context, R.layout.account_list_item) {

        override fun getView(position: Int, _v: View?, parent: ViewGroup): View {
            val account = getItem(position)!!

            val v = _v ?: LayoutInflater.from(context).inflate(R.layout.account_list_item, parent, false)
            v.account_name.text = account.name

            return v
        }
    }


    class Model(
            application: Application
    ): AndroidViewModel(application), OnAccountsUpdateListener {

        val accounts = MutableLiveData<Array<out Account>>()

        val networkAvailable = MutableLiveData<Boolean>()
        private var networkCallback: ConnectivityManager.NetworkCallback? = null
        private var networkReceiver: BroadcastReceiver? = null

        private val accountManager = AccountManager.get(getApplication())!!
        private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        init {
            accountManager.addOnAccountsUpdatedListener(this, null, true)

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
            val context = getApplication<Application>()
            accounts.postValue(
                    AccountManager.get(context).getAccountsByType(context.getString(R.string.account_type))
            )
        }

    }

}