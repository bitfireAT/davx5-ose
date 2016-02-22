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
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.widget.Toast;

import java.io.IOException;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.syncadapter.AccountSettings;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class DeleteCollectionFragment extends DialogFragment implements LoaderManager.LoaderCallbacks<Exception> {
    protected static final String
            ARG_ACCOUNT = "account",
            ARG_COLLECTION_INFO = "collectionInfo";

    public static DeleteCollectionFragment newInstance(Account account, CollectionInfo collectionInfo) {
        DeleteCollectionFragment frag = new DeleteCollectionFragment();
        Bundle args = new Bundle(2);
        args.putParcelable(ARG_ACCOUNT, account);
        args.putSerializable(ARG_COLLECTION_INFO, collectionInfo);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(0, getArguments(), this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new ProgressDialog.Builder(getContext())
                .setTitle("Deleting collection")
                .setMessage("Deleting collection from server, please wait.")
                .setCancelable(false)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }


    @Override
    public Loader<Exception> onCreateLoader(int id, Bundle args) {
        CollectionInfo collectionInfo = (CollectionInfo)args.getSerializable(ARG_COLLECTION_INFO);
        return new DeleteCollectionLoader(
                getContext(),
                (Account)args.getParcelable(ARG_ACCOUNT),
                collectionInfo.id,
                HttpUrl.parse(collectionInfo.url)
        );
    }

    @Override
    public void onLoadFinished(Loader<Exception> loader, Exception exception) {
        String msg = (exception == null) ? "Collection deleted" : exception.getLocalizedMessage();
        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
        dismissAllowingStateLoss();

        AccountActivity activity = (AccountActivity)getActivity();
        activity.reload();
    }

    @Override
    public void onLoaderReset(Loader<Exception> loader) {
    }

    private static class DeleteCollectionLoader extends AsyncTaskLoader<Exception> {
        final Account account;
        final long collectionId;
        final HttpUrl url;
        final ServiceDB.OpenHelper dbHelper;

        public DeleteCollectionLoader(Context context, Account account, long collectionId, HttpUrl url) {
            super(context);
            this.account = account;
            this.collectionId = collectionId;
            this.url = url;

            dbHelper = new ServiceDB.OpenHelper(context);
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public Exception loadInBackground() {
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            OkHttpClient httpClient = HttpClient.create(getContext());
            httpClient = HttpClient.addAuthentication(httpClient, new AccountSettings(getContext(), account));

            DavResource collection = new DavResource(null, httpClient, url);
            try {
                collection.delete(null);

                db.delete(ServiceDB.Collections._TABLE, ServiceDB.Collections.ID + "=?", new String[] { String.valueOf(collectionId) });

                return null;
            } catch (IOException|HttpException e) {
                return e;
            } finally {
                db.close();
            }
        }
    }
}
