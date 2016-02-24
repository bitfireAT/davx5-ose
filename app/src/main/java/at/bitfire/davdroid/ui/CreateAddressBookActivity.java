/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.NavUtils;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.XmlUtils;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.Service;
import at.bitfire.davdroid.model.HomeSet;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.syncadapter.AccountSettings;
import lombok.Cleanup;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class CreateAddressBookActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<CreateAddressBookActivity.AccountInfo> {
    public final static String EXTRA_ACCOUNT = "account";

    private Account account;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_address_book);

        account = getIntent().getParcelableExtra(EXTRA_ACCOUNT);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getSupportLoaderManager().initLoader(0, getIntent().getExtras(), this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_create_address_book, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, AccountActivity.class);
                intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account);
                NavUtils.navigateUpTo(this, intent);
                break;
            case R.id.create_address_book:
                createAddressBook();
                break;
            default:
                return false;
        }
        return true;
    }

    protected void createAddressBook() {
        Spinner spnrHomeSets = (Spinner)findViewById(R.id.homeset);
        HashMap<String, String> homeSet = (HashMap<String, String>)spnrHomeSets.getSelectedItem();
        HttpUrl urlHomeSet = HttpUrl.parse(homeSet.get(ServiceDB.HomeSets.URL));

        CollectionInfo info = new CollectionInfo();
        boolean ok = true;

        String displayName = ((EditText)findViewById(R.id.title)).getText().toString();
        if (!TextUtils.isEmpty(displayName))
            info.displayName = displayName;

        EditText editPathSegment = (EditText)findViewById(R.id.path_segment);
        String pathSegment = editPathSegment.getText().toString();
        if (TextUtils.isEmpty(pathSegment)) {   // TODO further validations
            editPathSegment.setError("MUST NOT BE EMPTY");
            ok = false;
        } else
            info.url = urlHomeSet.resolve(pathSegment).toString();

        String description = ((EditText)findViewById(R.id.description)).getText().toString();
        if (!TextUtils.isEmpty(description))
            info.description = description;

        if (ok)
            CreatingAddressBookFragment.newInstance(account, info).show(getSupportFragmentManager(), null);
    }


    public static class CreatingAddressBookFragment extends DialogFragment implements LoaderManager.LoaderCallbacks<Exception> {
        protected static final String
                ARGS_ACCOUNT = "account",
                ARGS_COLLECTION_INFO = "collectionInfo";

        public static CreatingAddressBookFragment newInstance(Account account, CollectionInfo info) {
            CreatingAddressBookFragment frag = new CreatingAddressBookFragment();
            Bundle args = new Bundle(2);
            args.putParcelable(ARGS_ACCOUNT, account);
            args.putSerializable(ARGS_COLLECTION_INFO, info);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            getLoaderManager().initLoader(0, getArguments(), this);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Dialog dialog = new ProgressDialog.Builder(getActivity())
                    .setTitle(R.string.create_address_book_creating)
                    .setMessage(R.string.please_wait)
                    .setCancelable(false)
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            return dialog;
        }

        @Override
        public Loader<Exception> onCreateLoader(int id, Bundle args) {
            Account account = (Account)args.getParcelable(ARGS_ACCOUNT);
            CollectionInfo info = (CollectionInfo)args.getSerializable(ARGS_COLLECTION_INFO);
            return new AddressBookCreator(getActivity(), account, info);
        }

        @Override
        public void onLoadFinished(Loader<Exception> loader, Exception exception) {
            dismissAllowingStateLoss();

            if (exception == null)
                getActivity().finish();
            else
                Toast.makeText(getActivity(), exception.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onLoaderReset(Loader<Exception> loader) {
        }

        protected static class AddressBookCreator extends AsyncTaskLoader<Exception> {
            final Account account;
            final CollectionInfo info;
            final ServiceDB.OpenHelper dbHelper;

            public AddressBookCreator(Context context, Account account, CollectionInfo collectionInfo) {
                super(context);
                this.account = account;
                info = collectionInfo;
                dbHelper = new ServiceDB.OpenHelper(context);
            }

            @Override
            protected void onStartLoading() {
                forceLoad();
            }

            @Override
            public Exception loadInBackground() {
                OkHttpClient client = HttpClient.create(getContext());
                client = HttpClient.addAuthentication(client, new AccountSettings(getContext(), account));

                StringWriter writer = new StringWriter();
                try {
                    XmlSerializer serializer = XmlUtils.newSerializer();
                    serializer.setOutput(writer);
                    serializer.startDocument("UTF-8", null);
                    serializer.setPrefix("", XmlUtils.NS_WEBDAV);
                    serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV);

                    serializer.startTag(XmlUtils.NS_WEBDAV, "mkcol");
                        serializer.startTag(XmlUtils.NS_WEBDAV, "set");
                            serializer.startTag(XmlUtils.NS_WEBDAV, "prop");
                                serializer.startTag(XmlUtils.NS_WEBDAV, "resourcetype");
                                    serializer.startTag(XmlUtils.NS_WEBDAV, "collection");
                                    serializer.endTag(XmlUtils.NS_WEBDAV, "collection");
                                    serializer.startTag(XmlUtils.NS_CARDDAV, "addressbook");
                                    serializer.endTag(XmlUtils.NS_CARDDAV, "addressbook");
                                serializer.endTag(XmlUtils.NS_WEBDAV, "resourcetype");
                                if (info.displayName != null) {
                                    serializer.startTag(XmlUtils.NS_WEBDAV, "displayname");
                                        serializer.text(info.displayName);
                                    serializer.endTag(XmlUtils.NS_WEBDAV, "displayname");
                                }
                                if (info.description != null) {
                                    serializer.startTag(XmlUtils.NS_CARDDAV, "addressbook-description");
                                        serializer.text(info.description);
                                    serializer.endTag(XmlUtils.NS_CARDDAV, "addressbook-description");
                                }
                            serializer.endTag(XmlUtils.NS_WEBDAV, "prop");
                        serializer.endTag(XmlUtils.NS_WEBDAV, "set");
                    serializer.endTag(XmlUtils.NS_WEBDAV, "mkcol");
                    serializer.endDocument();
                } catch (IOException e) {
                    Constants.log.error("Couldn't assemble MKCOL request", e);
                }

                DavResource addressBook = new DavResource(null, client, HttpUrl.parse(info.url));
                try {
                    addressBook.mkCol(writer.toString());

                    // TODO
                    /*SQLiteDatabase db = dbHelper.getWritableDatabase();
                    db.insert(ServiceDB.Collections._TABLE, null, info.toDB());*/

                    // TODO add to database
                } catch (IOException|HttpException e) {
                    return e;
                } finally {
                    dbHelper.close();
                }
                return null;
            }
        }
    }


    // loader

    @Override
    public Loader<AccountInfo> onCreateLoader(int id, Bundle args) {
        return new AccountLoader(this, account);
    }

    @Override
    public void onLoadFinished(Loader<AccountInfo> loader, AccountInfo data) {
        Spinner spnrHomeSets = (Spinner)findViewById(R.id.homeset);

        List<HashMap<String, String>> adapterData = new LinkedList<>();
        for (HomeSet homeSet : data.homeSets) {
            HashMap<String, String> map = new HashMap();
            map.put(ServiceDB.HomeSets.ID, String.valueOf(homeSet.id));
            map.put(ServiceDB.HomeSets.URL, homeSet.URL);
            adapterData.add(map);
        }

        spnrHomeSets.setAdapter(new SimpleAdapter(this, adapterData, android.R.layout.simple_spinner_item, new String[] { ServiceDB.HomeSets.URL }, new int[] { android.R.id.text1 } ));
    }

    @Override
    public void onLoaderReset(Loader<AccountInfo> loader) {
    }

    protected static class AccountInfo {
        Service service;
        List<HomeSet> homeSets = new LinkedList<>();
    }

    private static class AccountLoader extends AsyncTaskLoader<AccountInfo> {
        private final Account account;
        private final ServiceDB.OpenHelper dbHelper;

        public AccountLoader(Context context, Account account) {
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
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            try {
                @Cleanup Cursor cursorService = db.query(ServiceDB.Services._TABLE, null, ServiceDB.Services.ACCOUNT_NAME + "=? AND " + ServiceDB.Services.SERVICE + "=?",
                        new String[] { account.name, ServiceDB.Services.SERVICE_CARDDAV }, null, null, null);
                if (!cursorService.moveToNext())
                    return null;

                AccountInfo info = new AccountInfo();

                ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursorService, values);
                info.service = Service.fromDB(values);

                @Cleanup Cursor cursorHomeSets = db.query(ServiceDB.HomeSets._TABLE, null, ServiceDB.HomeSets.SERVICE_ID + "=?",
                        new String[] { String.valueOf(info.service.id) }, null, null, null);
                while (cursorHomeSets.moveToNext()) {
                    values.clear();
                    DatabaseUtils.cursorRowToContentValues(cursorHomeSets, values);
                    info.homeSets.add(HomeSet.fromDB(values));
                }

                return info;
            } finally {
                dbHelper.close();
            }
        }
    }
}
