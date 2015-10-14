/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;

import java.io.IOException;

import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.ui.DebugInfoActivity;
import at.bitfire.vcard4android.ContactsStorageException;

abstract public class SyncManager {

    protected final int SYNC_PHASE_PREPARE = 0,
                        SYNC_PHASE_QUERY_CAPABILITIES = 1,
                        SYNC_PHASE_PROCESS_LOCALLY_DELETED = 2,
                        SYNC_PHASE_PREPARE_LOCALLY_CREATED = 3,
                        SYNC_PHASE_UPLOAD_DIRTY = 4,
                        SYNC_PHASE_CHECK_SYNC_STATE = 5,
                        SYNC_PHASE_LIST_LOCAL = 6,
                        SYNC_PHASE_LIST_REMOTE = 7,
                        SYNC_PHASE_COMPARE_ENTRIES = 8,
                        SYNC_PHASE_DOWNLOAD_REMOTE = 9,
                        SYNC_PHASE_SAVE_SYNC_STATE = 10;

    final NotificationManager notificationManager;
    final int notificationId;

    final Context context;
    final Account account;
    final Bundle extras;
    final ContentProviderClient provider;
    final SyncResult syncResult;

    final AccountSettings settings;
    final HttpClient httpClient;

    public SyncManager(int notificationId, Context context, Account account, Bundle extras, ContentProviderClient provider, SyncResult syncResult) {
        this.context = context;
        this.account = account;
        this.extras = extras;
        this.provider = provider;
        this.syncResult = syncResult;

        // get account settings and generate httpClient
        settings = new AccountSettings(context, account);
        httpClient = new HttpClient(context, settings.getUserName(), settings.getPassword(), settings.getPreemptiveAuth());

        // dismiss previous error notifications
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(account.name, this.notificationId = notificationId);
    }

    public void performSync() {
        int syncPhase = SYNC_PHASE_PREPARE;
        try {
            prepare();

            syncPhase = SYNC_PHASE_QUERY_CAPABILITIES;
            queryCapabilities();

            syncPhase = SYNC_PHASE_PROCESS_LOCALLY_DELETED;
            processLocallyDeleted();

            syncPhase = SYNC_PHASE_PREPARE_LOCALLY_CREATED;
            processLocallyCreated();

            syncPhase = SYNC_PHASE_UPLOAD_DIRTY;
            uploadDirty();

            syncPhase = SYNC_PHASE_CHECK_SYNC_STATE;
            if (checkSyncState()) {
                syncPhase = SYNC_PHASE_LIST_LOCAL;
                listLocal();

                syncPhase = SYNC_PHASE_LIST_REMOTE;
                listRemote();

                syncPhase = SYNC_PHASE_COMPARE_ENTRIES;
                compareEntries();

                syncPhase = SYNC_PHASE_DOWNLOAD_REMOTE;
                downloadRemote();

                syncPhase = SYNC_PHASE_SAVE_SYNC_STATE;
                saveSyncState();
            } else
                Constants.log.info("Remote collection didn't change, skipping remote sync");

        } catch (IOException e) {
            Constants.log.error("I/O exception during sync, trying again later", e);
            syncResult.stats.numIoExceptions++;

        } catch(HttpException e) {
            Constants.log.error("HTTP Exception during sync", e);
            syncResult.stats.numParseExceptions++;

            Intent detailsIntent = new Intent(context, DebugInfoActivity.class);
            detailsIntent.putExtra(DebugInfoActivity.KEY_EXCEPTION, e);
            detailsIntent.putExtra(DebugInfoActivity.KEY_ACCOUNT, account);
            detailsIntent.putExtra(DebugInfoActivity.KEY_PHASE, syncPhase);

            Notification.Builder builder = new Notification.Builder(context);
            Notification notification;
            builder .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(context.getString(R.string.sync_error_title, account.name))
                    .setContentIntent(PendingIntent.getActivity(context, 0, detailsIntent, PendingIntent.FLAG_UPDATE_CURRENT));

            String[] phases = context.getResources().getStringArray(R.array.sync_error_phases);
            if (phases.length > syncPhase)
                builder.setContentText(context.getString(R.string.sync_error_http, phases[syncPhase]));

            if (Build.VERSION.SDK_INT >= 16) {
                if (Build.VERSION.SDK_INT >= 21)
                    builder.setCategory(Notification.CATEGORY_ERROR);
                notification = builder.build();
            } else {
                notification = builder.getNotification();
            }
            notificationManager.notify(account.name, notificationId, notification);

        } catch(DavException e) {
            // TODO
        } catch(ContactsStorageException e) {
            syncResult.databaseError = true;
        }

    }


    abstract protected void prepare();

    abstract protected void queryCapabilities() throws IOException, HttpException, DavException, ContactsStorageException;

    abstract protected void processLocallyDeleted() throws IOException, HttpException, DavException, ContactsStorageException;

    abstract protected void processLocallyCreated() throws IOException, HttpException, DavException, ContactsStorageException;

    abstract protected void uploadDirty() throws IOException, HttpException, DavException, ContactsStorageException;

    /**
     * Checks the current sync state (e.g. CTag) and whether synchronization from remote is required.
     * @return  true    if the remote collection has changed, i.e. synchronization from remote is required
     *          false   if the remote collection hasn't changed
     */
    abstract protected boolean checkSyncState() throws IOException, HttpException, DavException, ContactsStorageException;

    abstract protected void listLocal() throws IOException, HttpException, DavException, ContactsStorageException;

    abstract protected void listRemote() throws IOException, HttpException, DavException, ContactsStorageException;

    abstract protected void compareEntries() throws IOException, HttpException, DavException, ContactsStorageException;

    abstract protected void downloadRemote() throws IOException, HttpException, DavException, ContactsStorageException;

    abstract protected void saveSyncState() throws IOException, HttpException, DavException, ContactsStorageException;

}
