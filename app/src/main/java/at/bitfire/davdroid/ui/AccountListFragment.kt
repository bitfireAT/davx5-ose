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
import android.app.ListFragment
import android.app.LoaderManager
import android.content.AsyncTaskLoader
import android.content.Context
import android.content.Intent
import android.content.Loader
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import at.bitfire.davdroid.AccountsChangedReceiver
import at.bitfire.davdroid.R
import kotlinx.android.synthetic.main.account_list_item.view.*

class AccountListFragment: ListFragment(), LoaderManager.LoaderCallbacks<Array<Account>> {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        listAdapter = AccountListAdapter(activity)

        return inflater.inflate(R.layout.account_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loaderManager.initLoader(0, arguments, this)

        listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val account = listAdapter.getItem(position) as Account
            val intent = Intent(activity, AccountActivity::class.java)
            intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
            startActivity(intent)
        }
    }


    // loader

    override fun onCreateLoader(id: Int, args: Bundle?) =
            AccountLoader(activity)

    override fun onLoadFinished(loader: Loader<Array<Account>>, accounts: Array<Account>) {
        val adapter = listAdapter as AccountListAdapter
        adapter.clear()
        adapter.addAll(*accounts)
    }

    override fun onLoaderReset(loader: Loader<Array<Account>>) {
        (listAdapter as AccountListAdapter).clear()
    }

    class AccountLoader(
            context: Context
    ): AsyncTaskLoader<Array<Account>>(context), OnAccountsUpdateListener {

        override fun onStartLoading() =
                AccountsChangedReceiver.registerListener(this, true)

        override fun onStopLoading() =
                AccountsChangedReceiver.unregisterListener(this)

        override fun onAccountsUpdated(accounts: Array<Account>?) =
                forceLoad()

        override fun loadInBackground(): Array<Account> =
            AccountManager.get(context).getAccountsByType(context.getString(R.string.account_type))

    }


    // list adapter

    class AccountListAdapter(
            context: Context
    ): ArrayAdapter<Account>(context, R.layout.account_list_item) {

        override fun getView(position: Int, v: View?, parent: ViewGroup?): View {
            val account = getItem(position)

            val v = v ?: LayoutInflater.from(context).inflate(R.layout.account_list_item, parent, false)
            v.account_name.text = account.name

            return v
        }
    }

}
