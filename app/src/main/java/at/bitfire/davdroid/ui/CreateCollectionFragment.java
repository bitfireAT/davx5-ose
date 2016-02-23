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
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.widget.Toast;

import org.apache.commons.lang3.BooleanUtils;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.XmlUtils;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.DavUtils;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.syncadapter.AccountSettings;
import at.bitfire.ical4android.DateUtils;
import lombok.Cleanup;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class CreateCollectionFragment extends DialogFragment {
    private static final String
            ARG_ACCOUNT = "account",
            ARG_COLLECTION_INFO = "collectionInfo";

    public static CreateCollectionFragment newInstance(Account account, CollectionInfo info) {
        CreateCollectionFragment frag = new CreateCollectionFragment();
        Bundle args = new Bundle(2);
        args.putParcelable(ARG_ACCOUNT, account);
        args.putSerializable(ARG_COLLECTION_INFO, info);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        new CreateCollectionTask().execute(getArguments());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new ProgressDialog.Builder(getContext())
                .setTitle(R.string.create_collection_creating)
                .setMessage(R.string.please_wait)
                .create();
        setCancelable(false);
        return dialog;
    }


    protected class CreateCollectionTask extends AsyncTask<Bundle, Void, Exception> {
        @Override
        protected Exception doInBackground(Bundle... params) {
            Bundle args = params[0];
            Account account = args.getParcelable(ARG_ACCOUNT);
            CollectionInfo info = (CollectionInfo)args.getSerializable(ARG_COLLECTION_INFO);

            OkHttpClient client = HttpClient.create(getContext());
            client = HttpClient.addAuthentication(client, new AccountSettings(getContext(), account));

            StringWriter writer = new StringWriter();
            try {
                XmlSerializer serializer = XmlUtils.newSerializer();
                serializer.setOutput(writer);
                serializer.startDocument("UTF-8", null);
                serializer.setPrefix("", XmlUtils.NS_WEBDAV);
                serializer.setPrefix("CAL", XmlUtils.NS_CALDAV);
                serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV);

                serializer.startTag(XmlUtils.NS_WEBDAV, "mkcol");
                    serializer.startTag(XmlUtils.NS_WEBDAV, "set");
                        serializer.startTag(XmlUtils.NS_WEBDAV, "prop");
                            serializer.startTag(XmlUtils.NS_WEBDAV, "resourcetype");
                                serializer.startTag(XmlUtils.NS_WEBDAV, "collection");
                                serializer.endTag(XmlUtils.NS_WEBDAV, "collection");
                                if (info.type == CollectionInfo.Type.ADDRESS_BOOK) {
                                    serializer.startTag(XmlUtils.NS_CARDDAV, "addressbook");
                                    serializer.endTag(XmlUtils.NS_CARDDAV, "addressbook");
                                } else if (info.type == CollectionInfo.Type.CALENDAR) {
                                    serializer.startTag(XmlUtils.NS_CALDAV, "calendar");
                                    serializer.endTag(XmlUtils.NS_CALDAV, "calendar");
                                }
                            serializer.endTag(XmlUtils.NS_WEBDAV, "resourcetype");
                            if (info.displayName != null) {
                                serializer.startTag(XmlUtils.NS_WEBDAV, "displayname");
                                    serializer.text(info.displayName);
                                serializer.endTag(XmlUtils.NS_WEBDAV, "displayname");
                            }

                            // addressbook-specific properties
                            if (info.type == CollectionInfo.Type.ADDRESS_BOOK) {
                                if (info.description != null) {
                                    serializer.startTag(XmlUtils.NS_CARDDAV, "addressbook-description");
                                    serializer.text(info.description);
                                    serializer.endTag(XmlUtils.NS_CARDDAV, "addressbook-description");
                                }
                            }

                            // calendar-specific properties
                            if (info.type == CollectionInfo.Type.CALENDAR) {
                                if (info.description != null) {
                                    serializer.startTag(XmlUtils.NS_CALDAV, "calendar-description");
                                    serializer.text(info.description);
                                    serializer.endTag(XmlUtils.NS_CALDAV, "calendar-description");
                                }

                                if (info.color != null) {
                                    serializer.startTag(XmlUtils.NS_APPLE_ICAL, "calendar-color");
                                    serializer.text(DavUtils.ARGBtoCalDAVColor(info.color));
                                    serializer.endTag(XmlUtils.NS_APPLE_ICAL, "calendar-color");
                                }

                                if (info.timeZone != null) {
                                    serializer.startTag(XmlUtils.NS_CALDAV, "calendar-timezone");
                                    serializer.cdsect(info.timeZone);
                                    serializer.endTag(XmlUtils.NS_CALDAV, "calendar-timezone");
                                }

                                serializer.startTag(XmlUtils.NS_CALDAV, "supported-calendar-component-set");
                                if (BooleanUtils.isTrue(info.supportsVEVENT)) {
                                    serializer.startTag(XmlUtils.NS_CALDAV, "comp");
                                    serializer.attribute(null, "name", "VEVENT");
                                    serializer.endTag(XmlUtils.NS_CALDAV, "comp");
                                }
                                if (BooleanUtils.isTrue(info.supportsVTODO)) {
                                    serializer.startTag(XmlUtils.NS_CALDAV, "comp");
                                    serializer.attribute(null, "name", "VTODO");
                                    serializer.endTag(XmlUtils.NS_CALDAV, "comp");
                                }
                                serializer.endTag(XmlUtils.NS_CALDAV, "supported-calendar-component-set");
                            }

                        serializer.endTag(XmlUtils.NS_WEBDAV, "prop");
                    serializer.endTag(XmlUtils.NS_WEBDAV, "set");
                serializer.endTag(XmlUtils.NS_WEBDAV, "mkcol");
                serializer.endDocument();
            } catch (IOException e) {
                Constants.log.error("Couldn't assemble Extended MKCOL request", e);
            }

            ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(getContext());
            DavResource collection = new DavResource(null, client, HttpUrl.parse(info.url));
            try {
                // create collection on remote server
                collection.mkCol(writer.toString());

                // now insert collection into database:
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                // 1. find service ID
                String serviceType = null;
                if (info.type == CollectionInfo.Type.ADDRESS_BOOK)
                    serviceType = ServiceDB.Services.SERVICE_CARDDAV;
                else if (info.type == CollectionInfo.Type.CALENDAR)
                    serviceType = ServiceDB.Services.SERVICE_CALDAV;
                else
                    throw new IllegalArgumentException("Collection must be an address book or calendar");
                @Cleanup Cursor c = db.query(ServiceDB.Services._TABLE, new String[] { ServiceDB.Services.ID },
                        ServiceDB.Services.ACCOUNT_NAME + "=? AND " + ServiceDB.Services.SERVICE + "=?",
                        new String[] { account.name, serviceType }, null, null, null
                );
                if (!c.moveToNext())
                    throw new IllegalStateException();
                long serviceID = c.getLong(0);

                // 2. add collection to service
                ContentValues values = info.toDB();
                values.put(ServiceDB.Collections.SERVICE_ID, serviceID);
                db.insert(ServiceDB.Collections._TABLE, null, values);
            } catch(IOException|HttpException|IllegalStateException e) {
                return e;
            } finally {
                dbHelper.close();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Exception e) {
            dismissAllowingStateLoss();

            Activity parent = getActivity();
            if (parent != null) {
                if (e != null)
                    Toast.makeText(parent, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                else
                    parent.finish();
            }
        }
    }

}
