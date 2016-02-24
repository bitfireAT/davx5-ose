/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;

import java.io.IOException;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class DeleteCollectionFragment extends DialogFragment implements LoaderManager.LoaderCallbacks<Exception> {
    protected static final String
            ARG_ACCOUNT = "account",
            ARG_COLLECTION_INFO = "collectionInfo";

    protected Account account;
    protected CollectionInfo collectionInfo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getLoaderManager().initLoader(0, getArguments(), this);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new ProgressDialog.Builder(getContext())
                .setTitle(R.string.delete_collection_deleting_collection)
                .setMessage(R.string.please_wait)
                .create();
        setCancelable(false);
        return dialog;
    }


    @Override
    public Loader<Exception> onCreateLoader(int id, Bundle args) {
        account = args.getParcelable(ARG_ACCOUNT);
        collectionInfo = (CollectionInfo)args.getSerializable(ARG_COLLECTION_INFO);
        return new DeleteCollectionLoader(getContext(), account, collectionInfo);
    }

    @Override
    public void onLoadFinished(Loader loader, Exception exception) {
        dismissAllowingStateLoss();

        if (exception != null)
            getFragmentManager().beginTransaction()
                    .add(ExceptionInfoFragment.newInstance(exception, account), null)
                    .commitAllowingStateLoss();
        else {
            Activity activity = getActivity();
            if (activity instanceof AccountActivity)
                ((AccountActivity)activity).reload();
        }
    }

    @Override
    public void onLoaderReset(Loader loader) {
    }


    protected static class DeleteCollectionLoader extends AsyncTaskLoader<Exception> {
        final Account account;
        final CollectionInfo collectionInfo;
        final ServiceDB.OpenHelper dbHelper;

        public DeleteCollectionLoader(Context context, Account account, CollectionInfo collectionInfo) {
            super(context);
            this.account = account;
            this.collectionInfo = collectionInfo;
            dbHelper = new ServiceDB.OpenHelper(getContext());
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public Exception loadInBackground() {
            OkHttpClient httpClient = HttpClient.create(getContext(), account);

            DavResource collection = new DavResource(null, httpClient, HttpUrl.parse(collectionInfo.url));
            try {
                // delete collection from server
                collection.delete(null);

                // delete collection locally
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                db.delete(ServiceDB.Collections._TABLE, ServiceDB.Collections.ID + "=?", new String[] { String.valueOf(collectionInfo.id) });

                return null;
            } catch (IOException|HttpException e) {
                return e;
            } finally {
                dbHelper.close();
            }
        }
    }


    public static class ConfirmDeleteCollectionFragment extends DialogFragment {

        public static ConfirmDeleteCollectionFragment newInstance(Account account, CollectionInfo collectionInfo) {
            ConfirmDeleteCollectionFragment frag = new ConfirmDeleteCollectionFragment();
            Bundle args = new Bundle(2);
            args.putParcelable(ARG_ACCOUNT, account);
            args.putSerializable(ARG_COLLECTION_INFO, collectionInfo);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            CollectionInfo collectionInfo = (CollectionInfo)getArguments().getSerializable(ARG_COLLECTION_INFO);
            String name = TextUtils.isEmpty(collectionInfo.displayName) ? collectionInfo.url : collectionInfo.displayName;

            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.delete_collection_confirm_title)
                    .setMessage(getString(R.string.delete_collection_confirm_warning, name))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            DialogFragment frag = new DeleteCollectionFragment();
                            frag.setArguments(getArguments());
                            frag.show(getFragmentManager(), null);
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                        }
                    })
                    .create();
        }
    }

}
