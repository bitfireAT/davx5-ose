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
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.BitmapDrawable;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;
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
import java.util.logging.Level;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.UrlUtils;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.AddressbookHomeSet;
import at.bitfire.dav4android.property.CalendarHomeSet;
import at.bitfire.dav4android.property.CalendarProxyReadFor;
import at.bitfire.dav4android.property.CalendarProxyWriteFor;
import at.bitfire.dav4android.property.GroupMembership;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import at.bitfire.davdroid.model.ServiceDB.HomeSets;
import at.bitfire.davdroid.model.ServiceDB.OpenHelper;
import at.bitfire.davdroid.model.ServiceDB.Services;
import at.bitfire.davdroid.ui.DebugInfoActivity;
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
        if (intent != null) {
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
        App.log.info("Cleaning up orphaned accounts");

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
            Account account = null;

            try {
                db = dbHelper.getWritableDatabase();

                String serviceType = serviceType();
                App.log.info("Refreshing " + serviceType + " collections of service #" + service);

                // get account
                account = account();

                // create authenticating OkHttpClient (credentials taken from account settings)
                OkHttpClient httpClient = HttpClient.create(DavService.this, account);

                // refresh home sets: principal
                Set<HttpUrl> homeSets = readHomeSets();
                HttpUrl principal = readPrincipal();
                if (principal != null) {
                    App.log.fine("Querying principal for home sets");
                    DavResource dav = new DavResource(httpClient, principal);
                    queryHomeSets(serviceType, dav, homeSets);

                    // refresh home sets: calendar-proxy-read/write-for
                    CalendarProxyReadFor proxyRead = (CalendarProxyReadFor)dav.properties.get(CalendarProxyReadFor.NAME);
                    if (proxyRead != null)
                        for (String href : proxyRead.principals) {
                            App.log.fine("Principal is a read-only proxy for " + href + ", checking for home sets");
                            queryHomeSets(serviceType, new DavResource(httpClient, dav.location.resolve(href)), homeSets);
                        }
                    CalendarProxyWriteFor proxyWrite = (CalendarProxyWriteFor)dav.properties.get(CalendarProxyWriteFor.NAME);
                    if (proxyWrite != null)
                        for (String href : proxyWrite.principals) {
                            App.log.fine("Principal is a read-write proxy for " + href + ", checking for home sets");
                            queryHomeSets(serviceType, new DavResource(httpClient, dav.location.resolve(href)), homeSets);
                        }

                    // refresh home sets: direct group memberships
                    GroupMembership groupMembership = (GroupMembership)dav.properties.get(GroupMembership.NAME);
                    if (groupMembership != null)
                        for (String href : groupMembership.hrefs) {
                            App.log.fine("Principal is member of group " + href + ", checking for home sets");
                            DavResource group = new DavResource(httpClient, dav.location.resolve(href));
                            try {
                                queryHomeSets(serviceType, group, homeSets);
                            } catch(HttpException|DavException e) {
                                App.log.log(Level.WARNING, "Couldn't query member group ", e);
                            }
                        }
                }

                // now refresh collections (taken from home sets)
                Map<HttpUrl, CollectionInfo> collections = readCollections();

                // (remember selections before)
                Set<HttpUrl> selectedCollections = new HashSet<>();
                for (CollectionInfo info : collections.values())
                    if (info.selected)
                        selectedCollections.add(HttpUrl.parse(info.url));

                for (Iterator<HttpUrl> iterator = homeSets.iterator(); iterator.hasNext(); ) {
                    HttpUrl homeSet = iterator.next();
                    App.log.fine("Listing home set " + homeSet);

                    DavResource dav = new DavResource(httpClient, homeSet);
                    try {
                        dav.propfind(1, CollectionInfo.DAV_PROPERTIES);
                        for (DavResource member : dav.members) {
                            CollectionInfo info = CollectionInfo.fromDavResource(member);
                            info.confirmed = true;
                            App.log.log(Level.FINE, "Found collection", info);

                            if ((serviceType.equals(Services.SERVICE_CARDDAV) && info.type == CollectionInfo.Type.ADDRESS_BOOK) ||
                                (serviceType.equals(Services.SERVICE_CALDAV) && info.type == CollectionInfo.Type.CALENDAR))
                                collections.put(member.location, info);
                        }
                    } catch(HttpException e) {
                        if (e.status == 403 || e.status == 404 || e.status == 410)
                            // delete home set only if it was not accessible (40x)
                            iterator.remove();
                    }
                }

                // check/refresh unconfirmed collections
                for (Iterator<Map.Entry<HttpUrl, CollectionInfo>> iterator = collections.entrySet().iterator(); iterator.hasNext(); ) {
                    Map.Entry<HttpUrl, CollectionInfo> entry = iterator.next();
                    HttpUrl url = entry.getKey();
                    CollectionInfo info = entry.getValue();

                    if (!info.confirmed)
                        try {
                            DavResource dav = new DavResource(httpClient, url);
                            dav.propfind(0, CollectionInfo.DAV_PROPERTIES);
                            info = CollectionInfo.fromDavResource(dav);
                            info.confirmed = true;

                            // remove unusable collections
                            if ((serviceType.equals(Services.SERVICE_CARDDAV) && info.type != CollectionInfo.Type.ADDRESS_BOOK) ||
                                (serviceType.equals(Services.SERVICE_CALDAV) && info.type != CollectionInfo.Type.CALENDAR))
                                iterator.remove();
                        } catch(HttpException e) {
                            if (e.status == 403 || e.status == 404 || e.status == 410)
                                // delete collection only if it was not accessible (40x)
                                iterator.remove();
                            else
                                throw e;
                        }
                }

                // restore selections
                for (HttpUrl url : selectedCollections) {
                    CollectionInfo info = collections.get(url);
                    if (info != null)
                        info.selected = true;
                }

                try {
                    db.beginTransactionNonExclusive();
                    saveHomeSets(homeSets);
                    saveCollections(collections.values());
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }

            } catch(InvalidAccountException e) {
                App.log.log(Level.SEVERE, "Invalid account", e);
            } catch(IOException|HttpException|DavException e) {
                App.log.log(Level.SEVERE, "Couldn't refresh collection list", e);

                Intent debugIntent = new Intent(DavService.this, DebugInfoActivity.class);
                debugIntent.putExtra(DebugInfoActivity.KEY_THROWABLE, e);
                if (account != null)
                    debugIntent.putExtra(DebugInfoActivity.KEY_ACCOUNT, account);

                NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                Notification notify = new NotificationCompat.Builder(DavService.this)
                        .setSmallIcon(R.drawable.ic_error_light)
                        .setLargeIcon(((BitmapDrawable)getResources().getDrawable(R.drawable.ic_launcher)).getBitmap())
                        .setContentTitle(getString(R.string.dav_service_refresh_failed))
                        .setContentText(getString(R.string.dav_service_refresh_couldnt_refresh))
                        .setContentIntent(PendingIntent.getActivity(DavService.this, 0, debugIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                        .build();
                nm.notify(Constants.NOTIFICATION_REFRESH_COLLECTIONS, notify);
            } finally {
                dbHelper.close();

                runningRefresh.remove(service);
                for (RefreshingStatusListener listener : refreshingStatusListeners)
                    listener.onDavRefreshStatusChanged(service, false);
            }
        }

        /**
         * Checks if the given URL defines home sets and adds them to the home set list.
         * @param serviceType       CalDAV/CardDAV (calendar home set / addressbook home set)
         * @param dav               DavResource to check
         * @param homeSets          set where found home set URLs will be put into
         */
        private void queryHomeSets(String serviceType, DavResource dav, Set<HttpUrl> homeSets) throws IOException, HttpException, DavException {
            if (Services.SERVICE_CARDDAV.equals(serviceType)) {
                dav.propfind(0, AddressbookHomeSet.NAME, GroupMembership.NAME);
                AddressbookHomeSet addressbookHomeSet = (AddressbookHomeSet)dav.properties.get(AddressbookHomeSet.NAME);
                if (addressbookHomeSet != null)
                    for (String href : addressbookHomeSet.hrefs)
                        homeSets.add(UrlUtils.withTrailingSlash(dav.location.resolve(href)));
            } else if (Services.SERVICE_CALDAV.equals(serviceType)) {
                dav.propfind(0, CalendarHomeSet.NAME, CalendarProxyReadFor.NAME, CalendarProxyWriteFor.NAME, GroupMembership.NAME);
                CalendarHomeSet calendarHomeSet = (CalendarHomeSet)dav.properties.get(CalendarHomeSet.NAME);
                if (calendarHomeSet != null)
                    for (String href : calendarHomeSet.hrefs)
                        homeSets.add(UrlUtils.withTrailingSlash(dav.location.resolve(href)));
            }
        }


        @NonNull
        private Account account() {
            @Cleanup Cursor cursor = db.query(Services._TABLE, new String[] { Services.ACCOUNT_NAME }, Services.ID + "=?", new String[] { String.valueOf(service) }, null, null, null);
            if (cursor.moveToNext()) {
                return new Account(cursor.getString(0), Constants.ACCOUNT_TYPE);
            } else
                throw new IllegalArgumentException("Service not found");
        }

        @NonNull
        private String serviceType() {
            @Cleanup Cursor cursor = db.query(Services._TABLE, new String[] { Services.SERVICE }, Services.ID + "=?", new String[] { String.valueOf(service) }, null, null, null);
            if (cursor.moveToNext())
                return cursor.getString(0);
            else
                throw new IllegalArgumentException("Service not found");
        }

        @Nullable
        private HttpUrl readPrincipal() {
            @Cleanup Cursor cursor = db.query(Services._TABLE, new String[] { Services.PRINCIPAL }, Services.ID + "=?", new String[] { String.valueOf(service) }, null, null, null);
            if (cursor.moveToNext()) {
                String principal = cursor.getString(0);
                if (principal != null)
                    return HttpUrl.parse(cursor.getString(0));
            }
            return null;
        }

        @NonNull
        private Set<HttpUrl> readHomeSets() {
            Set<HttpUrl> homeSets = new LinkedHashSet<>();
            @Cleanup Cursor cursor = db.query(HomeSets._TABLE, new String[] { HomeSets.URL }, HomeSets.SERVICE_ID + "=?", new String[] { String.valueOf(service) }, null, null, null);
            while (cursor.moveToNext())
                homeSets.add(HttpUrl.parse(cursor.getString(0)));
            return homeSets;
        }

        private void saveHomeSets(Set<HttpUrl> homeSets) {
            db.delete(HomeSets._TABLE, HomeSets.SERVICE_ID + "=?", new String[] { String.valueOf(service) });
            for (HttpUrl homeSet : homeSets) {
                ContentValues values = new ContentValues(1);
                values.put(HomeSets.SERVICE_ID, service);
                values.put(HomeSets.URL, homeSet.toString());
                db.insertOrThrow(HomeSets._TABLE, null, values);
            }
        }

        @NonNull
        private Map<HttpUrl, CollectionInfo> readCollections() {
            Map<HttpUrl, CollectionInfo> collections = new LinkedHashMap<>();
            @Cleanup Cursor cursor = db.query(Collections._TABLE, null, Collections.SERVICE_ID + "=?", new String[]{String.valueOf(service)}, null, null, null);
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                collections.put(HttpUrl.parse(values.getAsString(Collections.URL)), CollectionInfo.fromDB(values));
            }
            return collections;
        }

        private void saveCollections(Iterable<CollectionInfo> collections) {
            db.delete(Collections._TABLE, HomeSets.SERVICE_ID + "=?", new String[] { String.valueOf(service) });
            for (CollectionInfo collection : collections) {
                ContentValues values = collection.toDB();
                App.log.log(Level.FINE, "Saving collection", values);
                values.put(Collections.SERVICE_ID, service);
                db.insertWithOnConflict(Collections._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
        }
    }

}
