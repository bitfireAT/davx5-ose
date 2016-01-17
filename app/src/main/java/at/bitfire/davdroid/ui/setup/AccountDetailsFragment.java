/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import java.util.Map;

import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.resource.DavResourceFinder;
import at.bitfire.davdroid.syncadapter.AccountSettings;
import at.bitfire.davdroid.syncadapter.ServiceDB.*;
import lombok.Cleanup;
import okhttp3.HttpUrl;

public class AccountDetailsFragment extends Fragment {

    private static final String KEY_CONFIG = "config";

    public static AccountDetailsFragment newInstance(DavResourceFinder.Configuration config) {
        Bundle args = new Bundle(1);
        args.putSerializable(KEY_CONFIG, config);

        AccountDetailsFragment fragment = new AccountDetailsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.login_account_details, container, false);

        Button btnBack = (Button)v.findViewById(R.id.back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getFragmentManager().popBackStack();
            }
        });

        final EditText editName = (EditText)v.findViewById(R.id.account_name);
        Button btnCreate = (Button)v.findViewById(R.id.create_account);
        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = editName.getText().toString();
                if (name.isEmpty())
                    editName.setError(getString(R.string.login_account_name_required));
                else {
                    if (createAccount(name, (DavResourceFinder.Configuration)getArguments().getSerializable(KEY_CONFIG)))
                        getActivity().finish();
                    else
                        Snackbar.make(v, R.string.login_account_not_created, Snackbar.LENGTH_LONG).show();
                }
            }
        });

        return v;
    }

    protected boolean createAccount(String accountName, DavResourceFinder.Configuration config) {
        Account account = new Account(accountName, Constants.ACCOUNT_TYPE);

        Constants.log.info("Creating account {}, initial config: {}", accountName, config);

        // create Android account
        AccountManager accountManager = AccountManager.get(getContext());
        Bundle userData = AccountSettings.initialUserData(config.userName, config.preemptive);
        if (!accountManager.addAccountExplicitly(account, config.password, userData))
            return false;

        // add entries for account to service DB
        @Cleanup OpenHelper dbHelper = new OpenHelper(getContext());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            if (config.cardDAV != null)
                insertService(db, accountName, Services.SERVICE_CARDDAV, config.cardDAV);

            if (config.calDAV != null)
                insertService(db, accountName, Services.SERVICE_CALDAV, config.calDAV);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        return true;
    }

    protected void insertService(SQLiteDatabase db, String accountName, String service, DavResourceFinder.Configuration.ServiceInfo info) {
        ContentValues values = new ContentValues();

        // insert service
        values.put(Services.ACCOUNT_NAME, accountName);
        values.put(Services.SERVICE, service);
        if (info.getPrincipal() != null)
            values.put(Services.PRINCIPAL, info.getPrincipal().toString());
        long serviceID = db.insertOrThrow(Services._TABLE, null, values);

        // insert home sets
        for (HttpUrl homeSet : info.getHomeSets()) {
            values.clear();
            values.put(HomeSets.SERVICE_ID, serviceID);
            values.put(HomeSets.URL, homeSet.toString());
            db.insertOrThrow(HomeSets._TABLE, null, values);
        }

        // insert collections
        for (Map.Entry<HttpUrl, DavResourceFinder.Configuration.Collection> entry : info.getCollections().entrySet()) {
            values = Collections.fromCollection(entry.getValue());
            values.put(Collections.SERVICE_ID, serviceID);
            values.put(Collections.URL, entry.getKey().toString());
            db.insertOrThrow(Collections._TABLE, null, values);
        }
    }

}
