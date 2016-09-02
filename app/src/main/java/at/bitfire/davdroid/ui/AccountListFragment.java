/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import at.bitfire.davdroid.AccountsChangedReceiver;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;

public class AccountListFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Account[]>, AdapterView.OnItemClickListener {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setListAdapter(new AccountListAdapter(getContext()));

        return inflater.inflate(R.layout.account_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getLoaderManager().initLoader(0, getArguments(), this);

        ListView list = getListView();
        list.setOnItemClickListener(this);
        list.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Account account = (Account)getListAdapter().getItem(position);

        Intent intent = new Intent(getContext(), AccountActivity.class);
        intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account);
        startActivity(intent);
    }


    // loader

    @Override
    public Loader<Account[]> onCreateLoader(int id, Bundle args) {
        return new AccountLoader(getContext());
    }

    @Override
    public void onLoadFinished(Loader<Account[]> loader, Account[] accounts) {
        AccountListAdapter adapter = (AccountListAdapter)getListAdapter();
        adapter.clear();
        adapter.addAll(accounts);
    }

    @Override
    public void onLoaderReset(Loader<Account[]> loader) {
        ((AccountListAdapter)getListAdapter()).clear();
    }

    private static class AccountLoader extends AsyncTaskLoader<Account[]> implements OnAccountsUpdateListener {
        private final AccountManager accountManager;

        public AccountLoader(Context context) {
            super(context);
            accountManager = AccountManager.get(context);
        }

        @Override
        protected void onStartLoading() {
            AccountsChangedReceiver.registerListener(this, true);
        }

        @Override
        protected void onStopLoading() {
            AccountsChangedReceiver.unregisterListener(this);
        }

        @Override
        public void onAccountsUpdated(Account[] accounts) {
            forceLoad();
        }

        @Override
        @SuppressLint("MissingPermission")
        public Account[] loadInBackground() {
            return accountManager.getAccountsByType(getContext().getString(R.string.account_type));
        }
    }


    // list adapter

    static class AccountListAdapter extends ArrayAdapter<Account> {
        public AccountListAdapter(Context context) {
            super(context, R.layout.account_list_item);
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            if (v == null)
                v = LayoutInflater.from(getContext()).inflate(R.layout.account_list_item, parent, false);

            Account account = getItem(position);

            TextView tv = (TextView)v.findViewById(R.id.account_name);
            tv.setText(account.name);

            return v;
        }
    }

}
