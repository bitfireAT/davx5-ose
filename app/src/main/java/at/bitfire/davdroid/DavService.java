/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.os.IBinder;
import android.text.TextUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.UrlUtils;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.exception.NotFoundException;
import at.bitfire.dav4android.property.AddressbookHomeSet;
import at.bitfire.dav4android.property.CalendarHomeSet;
import at.bitfire.dav4android.property.GroupMembership;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB.*;
import lombok.Cleanup;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class DavService extends Service {

    public static final String
            ACTION_ACCOUNTS_UPDATED = "accountsUpdated",
            ACTION_REFRESH_COLLECTIONS = "refreshCollections",
            EXTRA_DAV_SERVICE_ID = "davServiceID";

    private final IBinder binder = new InfoBinder();

    private final Set<Long> runningRefresh = new HashSet<>();
    private final List<RefreshingStatusListener> refreshingStatusListeners = new LinkedList<>();


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        long id = intent.getLongExtra(EXTRA_DAV_SERVICE_ID, -1);

        switch (action) {
            case ACTION_ACCOUNTS_UPDATED:
                cleanupAccounts();
                break;
            case ACTION_REFRESH_COLLECTIONS:
                if (runningRefresh.add(id)) {
                    new Thread(new RefreshCollections(id)).start();
                    for (RefreshingStatusListener listener : refreshingStatusListeners)
                        listener.onDavRefreshStatusChanged(id, true);
                }
                break;
        }

        return START_NOT_STICKY;
    }


    /* BOUND SERVICE PART
       for communicating with the activities
    */

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public interface RefreshingStatusListener {
        void onDavRefreshStatusChanged(long id, boolean refreshing);
    }

    public class InfoBinder extends Binder {
        public boolean isRefreshing(long id) {
            return runningRefresh.contains(id);
        }

        public void addRefreshingStatusListener(RefreshingStatusListener listener, boolean callImmediate) {
            refreshingStatusListeners.add(listener);
            if (callImmediate)
                for (long id : runningRefresh)
                    listener.onDavRefreshStatusChanged(id, true);
        }

        public void removeRefreshingStatusListener(RefreshingStatusListener listener) {
            refreshingStatusListeners.remove(listener);
        }
    }


    /* ACTION RUNNABLES
       which actually do the work
     */

    void cleanupAccounts() {
        Constants.log.info("[DavService] Cleaning up orphaned accounts");

        final OpenHelper dbHelper = new OpenHelper(this);
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            List<String> sqlAccountNames = new LinkedList<>();
            AccountManager am = AccountManager.get(this);
            for (Account account : am.getAccountsByType(Constants.ACCOUNT_TYPE))
                sqlAccountNames.add(DatabaseUtils.sqlEscapeString(account.name));

            if (sqlAccountNames.isEmpty())
                db.delete(Services._TABLE, null, null);
            else
                db.delete(Services._TABLE, Services.ACCOUNT_NAME + " NOT IN (" + TextUtils.join(",", sqlAccountNames) + ")", null);
        } finally {
            dbHelper.close();
        }
    }

    private class RefreshCollections implements Runnable {
        final long service;
        final OpenHelper dbHelper;
        SQLiteDatabase db;

        RefreshCollections(long davServiceId) {
            this.service = davServiceId;
            dbHelper = new OpenHelper(DavService.this);
        }

        @Override
        public void run() {
            try {
                db = dbHelper.getWritableDatabase();

                String serviceType = serviceType();
                Constants.log.info("[DavService {}] Refreshing {} collections", service, serviceType);

                // create authenticating OkHttpClient (credentials taken from account settings)
                OkHttpClient httpClient = httpClient();

                // refresh home sets: principal
                Set<HttpUrl> homeSets = readHomeSets();
                HttpUrl principal = readPrincipal();
                if (principal != null) {
                    Constants.log.debug("[DavService {}] Querying principal for home sets", service);
                    DavResource dav = new DavResource(null, httpClient, principal);
                    queryHomeSets(serviceType, httpClient, principal, homeSets);

                    // refresh home sets: direct group memberships
                    GroupMembership groupMembership = (GroupMembership)dav.properties.get(GroupMembership.NAME);
                    if (groupMembership != null)
                        for (String href : groupMembership.hrefs) {
                            Constants.log.debug("[DavService {}] Querying member group {} for home sets", href);
                            queryHomeSets(serviceType, httpClient, dav.location.resolve(href), homeSets);
                        }
                }

                // now refresh collections (taken from home sets)
                Map<HttpUrl, CollectionInfo> collections = readCollections();

                // (remember selections before)
                Set<HttpUrl> selectedCollections = new HashSet<>();
                for (CollectionInfo info : collections.values())
                    if (info.selected)
                        selectedCollections.add(HttpUrl.parse(info.url));

                for (Iterator<HttpUrl> iterator = homeSets.iterator(); iterator.hasNext();) {
                    HttpUrl homeSet = iterator.next();
                    Constants.log.debug("[DavService {}] Listing home set {}", service, homeSet);

                    DavResource dav = new DavResource(null, httpClient, homeSet);
                    try {
                        dav.propfind(1, CollectionInfo.DAV_PROPERTIES);
                        for (DavResource member : dav.members) {
                            CollectionInfo info = CollectionInfo.fromDavResource(member);
                            info.confirmed = true;
                            Constants.log.debug("[DavService {}] Found collection {}", service, info);

                            if ((serviceType.equals(Services.SERVICE_CARDDAV) && info.type == CollectionInfo.Type.ADDRESS_BOOK) ||
                                (serviceType.equals(Services.SERVICE_CALDAV) && info.type == CollectionInfo.Type.CALENDAR))
                                collections.put(member.location, info);
                        }
                    } catch(NotFoundException e) {
                        // 404, remove home set
                        iterator.remove();
                    }
                }

                // check/refresh unconfirmed collections
                for (Iterator<Map.Entry<HttpUrl, CollectionInfo>> iterator = collections.entrySet().iterator(); iterator.hasNext();) {
                    Map.Entry<HttpUrl, CollectionInfo> entry = iterator.next();
                    HttpUrl url = entry.getKey();
                    CollectionInfo info = entry.getValue();

                    if (!info.confirmed)
                        try {
                            DavResource dav = new DavResource(null, httpClient, url);
                            dav.propfind(0, CollectionInfo.DAV_PROPERTIES);
                            info = CollectionInfo.fromDavResource(dav);
                            info.confirmed = true;

                            // remove unusable collections
                            if ((serviceType.equals(Services.SERVICE_CARDDAV) && info.type != CollectionInfo.Type.ADDRESS_BOOK) ||
                                (serviceType.equals(Services.SERVICE_CALDAV) && info.type != CollectionInfo.Type.CALENDAR))
                                iterator.remove();
                        } catch(NotFoundException e) {
                            // 404, remove collection
                            iterator.remove();
                        }
                }

                // restore selections
                for (HttpUrl url : selectedCollections) {
                    CollectionInfo info = collections.get(url);
                    if (info != null)
                        info.selected = true;
                }

                try {
                    db.beginTransaction();
                    saveHomeSets(homeSets);
                    saveCollections(collections.values());
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }

            } catch (IOException|HttpException|DavException e) {
                Constants.log.error("Couldn't refresh collection list", e);
            } finally {
                dbHelper.close();

                runningRefresh.remove(service);
                for (RefreshingStatusListener listener : refreshingStatusListeners)
                    listener.onDavRefreshStatusChanged(service, false);
            }
        }

        private void queryHomeSets(String serviceType, OkHttpClient client, HttpUrl url, Set<HttpUrl> homeSets) throws IOException, HttpException, DavException {
            DavResource dav = new DavResource(null, client, url);
            if (Services.SERVICE_CARDDAV.equals(serviceType)) {
                dav.propfind(0, AddressbookHomeSet.NAME, GroupMembership.NAME);
                AddressbookHomeSet addressbookHomeSet = (AddressbookHomeSet)dav.properties.get(AddressbookHomeSet.NAME);
                if (addressbookHomeSet != null)
                    for (String href : addressbookHomeSet.hrefs)
                        homeSets.add(UrlUtils.withTrailingSlash(dav.location.resolve(href)));
            } else if (Services.SERVICE_CALDAV.equals(serviceType)) {
                dav.propfind(0, CalendarHomeSet.NAME, GroupMembership.NAME);
                CalendarHomeSet calendarHomeSet = (CalendarHomeSet)dav.properties.get(CalendarHomeSet.NAME);
                if (calendarHomeSet != null)
                    for (String href : calendarHomeSet.hrefs)
                        homeSets.add(UrlUtils.withTrailingSlash(dav.location.resolve(href)));
            }
        }


        private OkHttpClient httpClient() {
            @Cleanup Cursor cursor = db.query(Services._TABLE, new String[]{Services.ACCOUNT_NAME}, Services.ID + "=?", new String[] { String.valueOf(service) }, null, null, null);
            if (cursor.moveToNext()) {
                Account account = new Account(cursor.getString(0), Constants.ACCOUNT_TYPE);
                return HttpClient.create(DavService.this, account);
            } else
                throw new IllegalArgumentException("Service not found");
        }

        private String serviceType() {
            @Cleanup Cursor cursor = db.query(Services._TABLE, new String[]{Services.SERVICE}, Services.ID + "=?", new String[] { String.valueOf(service) }, null, null, null);
            if (cursor.moveToNext())
                return cursor.getString(0);
            else
                throw new IllegalArgumentException("Service not found");
        }

        private HttpUrl readPrincipal() {
            @Cleanup Cursor cursor = db.query(Services._TABLE, new String[]{Services.PRINCIPAL}, Services.ID + "=?", new String[]{String.valueOf(service)}, null, null, null);
            if (cursor.moveToNext()) {
                String principal = cursor.getString(0);
                if (principal != null)
                    return HttpUrl.parse(cursor.getString(0));
            }
            return null;
        }

        private Set<HttpUrl> readHomeSets() {
            Set<HttpUrl> homeSets = new LinkedHashSet<>();
            @Cleanup Cursor cursor = db.query(HomeSets._TABLE, new String[]{HomeSets.URL}, HomeSets.SERVICE_ID + "=?", new String[]{String.valueOf(service)}, null, null, null);
            while (cursor.moveToNext())
                homeSets.add(HttpUrl.parse(cursor.getString(0)));
            return homeSets;
        }

        private void saveHomeSets(Set<HttpUrl> homeSets) {
            db.delete(HomeSets._TABLE, HomeSets.SERVICE_ID + "=?", new String[]{String.valueOf(service)});
            for (HttpUrl homeSet : homeSets) {
                ContentValues values = new ContentValues(1);
                values.put(HomeSets.SERVICE_ID, service);
                values.put(HomeSets.URL, homeSet.toString());
                db.insertOrThrow(HomeSets._TABLE, null, values);
            }
        }

        private Map<HttpUrl, CollectionInfo> readCollections() {
            Map<HttpUrl, CollectionInfo> collections = new LinkedHashMap<>();
            @Cleanup Cursor cursor = db.query(Collections._TABLE, Collections._COLUMNS, Collections.SERVICE_ID + "=?", new String[]{String.valueOf(service)}, null, null, null);
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                collections.put(HttpUrl.parse(values.getAsString(Collections.URL)), CollectionInfo.fromDB(values));
            }
            return collections;
        }

        private void saveCollections(Iterable<CollectionInfo> collections) {
            db.delete(Collections._TABLE, HomeSets.SERVICE_ID + "=?", new String[]{String.valueOf(service)});
            for (CollectionInfo collection : collections) {
                ContentValues values = collection.toDB();
                Constants.log.debug("Saving collection: {}", values);
                values.put(Collections.SERVICE_ID, service);
                db.insertWithOnConflict(Collections._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
        }
    }

}
