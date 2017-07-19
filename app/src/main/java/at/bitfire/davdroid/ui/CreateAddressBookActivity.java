/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.NavUtils;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB;
import lombok.Cleanup;
import okhttp3.HttpUrl;

public class CreateAddressBookActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<CreateAddressBookActivity.AccountInfo> {
    public static final String EXTRA_ACCOUNT = "account";

    protected Account account;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        account = getIntent().getParcelableExtra(EXTRA_ACCOUNT);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.activity_create_address_book);

        getSupportLoaderManager().initLoader(0, getIntent().getExtras(), this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_create_collection, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent intent = new Intent(this, AccountActivity.class);
            intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account);
            NavUtils.navigateUpTo(this, intent);
            return true;
        }
        return false;
    }

    public void onCreateCollection(MenuItem item) {
        Spinner spinner = (Spinner)findViewById(R.id.home_sets);
        String homeSet = (String)spinner.getSelectedItem();

        boolean ok = true;
        CollectionInfo info = new CollectionInfo(HttpUrl.parse(homeSet).resolve(UUID.randomUUID().toString() + "/").toString());

        EditText edit = (EditText)findViewById(R.id.display_name);
        info.setDisplayName(edit.getText().toString());
        if (TextUtils.isEmpty(info.getDisplayName())) {
            edit.setError(getString(R.string.create_collection_display_name_required));
            ok = false;
        }

        edit = (EditText)findViewById(R.id.description);
        info.setDescription(StringUtils.trimToNull(edit.getText().toString()));

        if (ok) {
            info.setType(CollectionInfo.Type.ADDRESS_BOOK);
            CreateCollectionFragment.newInstance(account, info).show(getSupportFragmentManager(), null);
        }
    }


    @Override
    public Loader<AccountInfo> onCreateLoader(int id, Bundle args) {
        return new AccountInfoLoader(this, account);
    }

    @Override
    public void onLoadFinished(Loader<AccountInfo> loader, AccountInfo info) {
        if (info != null) {
            Spinner spinner = (Spinner)findViewById(R.id.home_sets);
            spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, info.homeSets));
        }
    }

    @Override
    public void onLoaderReset(Loader<AccountInfo> loader) {
    }

    protected static class AccountInfo {
        List<String> homeSets = new LinkedList<>();
    }

    protected static class AccountInfoLoader extends AsyncTaskLoader<AccountInfo> {
        private final Account account;
        private final ServiceDB.OpenHelper dbHelper;

        public AccountInfoLoader(Context context, Account account) {
            super(context);
            this.account = account;
            dbHelper = new ServiceDB.OpenHelper(context);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public AccountInfo loadInBackground() {
            final AccountInfo info = new AccountInfo();

            // find DAV service and home sets
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            try {
                @Cleanup Cursor cursorService = db.query(ServiceDB.Services._TABLE, new String[] { ServiceDB.Services.ID },
                        ServiceDB.Services.ACCOUNT_NAME + "=? AND " + ServiceDB.Services.SERVICE + "=?",
                        new String[] { account.name, ServiceDB.Services.SERVICE_CARDDAV }, null, null, null);
                if (!cursorService.moveToNext())
                    return null;
                String strServiceID = cursorService.getString(0);

                @Cleanup Cursor cursorHomeSets = db.query(ServiceDB.HomeSets._TABLE, new String[] { ServiceDB.HomeSets.URL },
                        ServiceDB.HomeSets.SERVICE_ID + "=?", new String[] { strServiceID }, null, null, null);
                while (cursorHomeSets.moveToNext())
                    info.homeSets.add(cursorHomeSets.getString(0));
            } finally {
                dbHelper.close();
            }

            return info;
        }
    }
}
