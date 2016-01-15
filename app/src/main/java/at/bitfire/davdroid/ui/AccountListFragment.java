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
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;

public class AccountListFragment extends ListFragment implements OnAccountsUpdateListener {

    protected AccountManager accountManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setListAdapter(new AccountListAdapter(getContext()));

        accountManager = AccountManager.get(getContext());
        accountManager.addOnAccountsUpdatedListener(this, null, true);

        return inflater.inflate(R.layout.account_list, container, false);
    }

    @Override
    public void onDestroyView() {
        accountManager.removeOnAccountsUpdatedListener(this);
        super.onDestroyView();
    }

    @Override
    public void onAccountsUpdated(Account[] accounts) {
        AccountListAdapter adapter = (AccountListAdapter)getListAdapter();
        if (adapter != null) {
            adapter.clear();
            for (Account account : accounts)
                if (Constants.ACCOUNT_TYPE.equals(account.type))
                    adapter.add(account);
        }
    }


    class AccountListAdapter extends ArrayAdapter<Account> {

        public AccountListAdapter(Context context) {
            super(context, R.layout.account_list_item);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;

            if (v == null) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                v = inflater.inflate(R.layout.account_list_item, parent, false);
            }

            Account account = getItem(position);

            TextView tvName = (TextView)v.findViewById(R.id.account_name);
            tvName.setText(account.name);

            return v;
        }
    }
}
