/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
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
import at.bitfire.davdroid.model.DavService;
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
                intent.putExtra(AccountActivity.EXTRA_ACCOUNT_NAME, account.name);
                startActivity(intent);
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
        info.url = urlHomeSet.resolve("myAddrBook.vcf").toString();
        info.displayName = "myAddrBook";

    new AddressBookCreator().execute(   info);
    }


    // AsyncTask for creating the address book

    class AddressBookCreator extends AsyncTask<CollectionInfo, Void, Exception> {
        @Override
        protected void onPostExecute(Exception e) {
            String msg = (e == null) ? "Created!" : e.getLocalizedMessage();
            Toast.makeText(CreateAddressBookActivity.this, msg, Toast.LENGTH_LONG).show();
        }

        @Override
        protected Exception doInBackground(CollectionInfo[] infoArray) {
            AccountSettings accountSettings = new AccountSettings(CreateAddressBookActivity.this, account);

            CollectionInfo info = infoArray[0];
            OkHttpClient client = HttpClient.create(CreateAddressBookActivity.this);
            client = HttpClient.addAuthentication(client, accountSettings.username(), accountSettings.password(), accountSettings.preemptiveAuth());

            DavResource addressBook = new DavResource(null, client, HttpUrl.parse(info.url));

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
                            serializer.startTag(XmlUtils.NS_WEBDAV, "displayname");
                                serializer.text(info.displayName);
                            serializer.endTag(XmlUtils.NS_WEBDAV, "displayname");
                        serializer.endTag(XmlUtils.NS_WEBDAV, "prop");
                    serializer.endTag(XmlUtils.NS_WEBDAV, "set");
                serializer.endTag(XmlUtils.NS_WEBDAV, "mkcol");
                serializer.endDocument();
            } catch (IOException e) {
                Constants.log.error("Couldn't assemble MKCOL request", e);
            }

            String error = null;
            try {
                addressBook.mkCol(writer.toString());
            } catch (IOException|HttpException e) {
                return e;
            }
            return null;
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
        DavService service;
        List<HomeSet> homeSets = new LinkedList<>();
    }

    private static class AccountLoader extends AsyncTaskLoader<AccountInfo> {
        private final Account account;
        ServiceDB.OpenHelper dbHelper;

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
                info.service = DavService.fromDB(values);

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
