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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import java.net.URI;
import java.util.logging.Level;

import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.DavService;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import at.bitfire.davdroid.model.ServiceDB.HomeSets;
import at.bitfire.davdroid.model.ServiceDB.OpenHelper;
import at.bitfire.davdroid.model.ServiceDB.Services;
import at.bitfire.davdroid.resource.LocalTaskList;
import at.bitfire.ical4android.TaskProvider;
import lombok.Cleanup;

public class AccountDetailsFragment extends Fragment {

    private static final String KEY_CONFIG = "config";
    private static final int DEFAULT_SYNC_INTERVAL = 4 * 3600;  // 4 hours

    public static AccountDetailsFragment newInstance(DavResourceFinder.Configuration config) {
        AccountDetailsFragment frag = new AccountDetailsFragment();
        Bundle args = new Bundle(1);
        args.putSerializable(KEY_CONFIG, config);
        frag.setArguments(args);
        return frag;
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

        DavResourceFinder.Configuration config = (DavResourceFinder.Configuration)getArguments().getSerializable(KEY_CONFIG);

        final EditText editName = (EditText)v.findViewById(R.id.account_name);
        editName.setText(config.userName);

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

        // create Android account
        Bundle userData = AccountSettings.initialUserData(config.userName, config.preemptive);
        App.log.log(Level.INFO, "Creating Android account with initial config", new Object[] { account, userData });

        AccountManager accountManager = AccountManager.get(getContext());
        if (!accountManager.addAccountExplicitly(account, config.password, userData))
            return false;

        // add entries for account to service DB
        App.log.log(Level.INFO, "Writing account configuration to database", config);
        @Cleanup OpenHelper dbHelper = new OpenHelper(getContext());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            AccountSettings settings = new AccountSettings(getContext(), account);

            Intent refreshIntent = new Intent(getActivity(), DavService.class);
            refreshIntent.setAction(DavService.ACTION_REFRESH_COLLECTIONS);

            db.beginTransactionNonExclusive();
            if (config.cardDAV != null) {
                long id = insertService(db, accountName, Services.SERVICE_CARDDAV, config.cardDAV);
                refreshIntent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, id);
                getActivity().startService(refreshIntent);

                ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
                settings.setSyncInterval(ContactsContract.AUTHORITY, DEFAULT_SYNC_INTERVAL);
            } else
                ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 0);

            if (config.calDAV != null) {
                long id = insertService(db, accountName, Services.SERVICE_CALDAV, config.calDAV);
                refreshIntent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, id);
                getActivity().startService(refreshIntent);

                ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1);
                settings.setSyncInterval(CalendarContract.AUTHORITY, DEFAULT_SYNC_INTERVAL);

                if (Build.VERSION.SDK_INT >= 23 || LocalTaskList.tasksProviderAvailable(getContext())) {
                    ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 1);
                    settings.setSyncInterval(TaskProvider.ProviderName.OpenTasks.authority, DEFAULT_SYNC_INTERVAL);
                } else
                    // Android <6 only: disable OpenTasks sync forever when OpenTasks is not installed
                    // because otherwise, there will be a non-catchable SecurityException as soon as OpenTasks is installed
                    ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 0);
            } else {
                ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 0);
                ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 0);
            }

            db.setTransactionSuccessful();
        } catch(InvalidAccountException e) {
            App.log.log(Level.SEVERE, "Couldn't access account settings", e);
        } finally {
            db.endTransaction();
        }

        return true;
    }

    protected long insertService(SQLiteDatabase db, String accountName, String service, DavResourceFinder.Configuration.ServiceInfo info) {
        ContentValues values = new ContentValues();

        // insert service
        values.put(Services.ACCOUNT_NAME, accountName);
        values.put(Services.SERVICE, service);
        if (info.principal != null)
            values.put(Services.PRINCIPAL, info.principal.toString());
        long serviceID = db.insertWithOnConflict(Services._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        // insert home sets
        for (URI homeSet : info.homeSets) {
            values.clear();
            values.put(HomeSets.SERVICE_ID, serviceID);
            values.put(HomeSets.URL, homeSet.toString());
            db.insertWithOnConflict(HomeSets._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        // insert collections
        for (CollectionInfo collection : info.collections.values()) {
            values = collection.toDB();
            values.put(Collections.SERVICE_ID, serviceID);
            db.insertWithOnConflict(Collections._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        return serviceID;
    }

}
