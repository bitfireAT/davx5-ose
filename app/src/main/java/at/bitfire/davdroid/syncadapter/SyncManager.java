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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.RequestBody;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.exception.PreconditionFailedException;
import at.bitfire.dav4android.exception.ServiceUnavailableException;
import at.bitfire.dav4android.property.GetCTag;
import at.bitfire.dav4android.property.GetETag;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.resource.LocalCollection;
import at.bitfire.davdroid.resource.LocalResource;
import at.bitfire.davdroid.ui.DebugInfoActivity;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.vcard4android.ContactsStorageException;

abstract public class SyncManager {

    protected final int SYNC_PHASE_PREPARE = 0,
                        SYNC_PHASE_QUERY_CAPABILITIES = 1,
                        SYNC_PHASE_PROCESS_LOCALLY_DELETED = 2,
                        SYNC_PHASE_PREPARE_DIRTY = 3,
                        SYNC_PHASE_UPLOAD_DIRTY = 4,
                        SYNC_PHASE_CHECK_SYNC_STATE = 5,
                        SYNC_PHASE_LIST_LOCAL = 6,
                        SYNC_PHASE_LIST_REMOTE = 7,
                        SYNC_PHASE_COMPARE_LOCAL_REMOTE = 8,
                        SYNC_PHASE_DOWNLOAD_REMOTE = 9,
                        SYNC_PHASE_SAVE_SYNC_STATE = 10;

    protected final NotificationManager notificationManager;
    protected final int notificationId;

    protected final Context context;
    protected final Account account;
    protected final Bundle extras;
    protected final ContentProviderClient provider;
    protected final SyncResult syncResult;

    protected final AccountSettings settings;
    protected LocalCollection localCollection;

    protected final HttpClient httpClient;
    protected HttpUrl collectionURL;
    protected DavResource davCollection;


    /** remote CTag at the time of {@link #listRemote()} */
    protected String remoteCTag = null;

    /** sync-able resources in the local collection, as enumerated by {@link #listLocal()} */
    protected Map<String, LocalResource> localResources;

    /** sync-able resources in the remote collection, as enumerated by {@link #listRemote()} */
    protected Map<String, DavResource> remoteResources;

    /** resources which have changed on the server, as determined by {@link #compareLocalRemote()} */
    protected Set<DavResource> toDownload;



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
            Constants.log.info("Preparing synchronization");
            prepare();

            syncPhase = SYNC_PHASE_QUERY_CAPABILITIES;
            Constants.log.info("Querying capabilities");
            queryCapabilities();

            syncPhase = SYNC_PHASE_PROCESS_LOCALLY_DELETED;
            Constants.log.info("Processing locally deleted entries");
            processLocallyDeleted();

            syncPhase = SYNC_PHASE_PREPARE_DIRTY;
            Constants.log.info("Locally preparing dirty entries");
            prepareDirty();

            syncPhase = SYNC_PHASE_UPLOAD_DIRTY;
            Constants.log.info("Uploading dirty entries");
            uploadDirty();

            syncPhase = SYNC_PHASE_CHECK_SYNC_STATE;
            Constants.log.info("Checking sync state");
            if (checkSyncState()) {
                syncPhase = SYNC_PHASE_LIST_LOCAL;
                Constants.log.info("Listing local entries");
                listLocal();

                syncPhase = SYNC_PHASE_LIST_REMOTE;
                Constants.log.info("Listing remote entries");
                listRemote();

                syncPhase = SYNC_PHASE_COMPARE_LOCAL_REMOTE;
                Constants.log.info("Comparing local/remote entries");
                compareLocalRemote();

                syncPhase = SYNC_PHASE_DOWNLOAD_REMOTE;
                Constants.log.info("Downloading remote entries");
                downloadRemote();

                syncPhase = SYNC_PHASE_SAVE_SYNC_STATE;
                Constants.log.info("Saving sync state");
                saveSyncState();
            } else
                Constants.log.info("Remote collection didn't change, skipping remote sync");

        } catch (IOException|ServiceUnavailableException e) {
            Constants.log.error("I/O exception during sync, trying again later", e);
            syncResult.stats.numIoExceptions++;

            if (e instanceof ServiceUnavailableException) {
                Date retryAfter = ((ServiceUnavailableException) e).retryAfter;
                if (retryAfter != null) {
                    // how many seconds to wait? getTime() returns ms, so divide by 1000
                    syncResult.delayUntil = (retryAfter.getTime() - new Date().getTime()) / 1000;
                }
            }

        } catch(HttpException|DavException e) {
            Constants.log.error("HTTP/DAV Exception during sync", e);
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

        } catch(CalendarStorageException|ContactsStorageException e) {
            Constants.log.error("Couldn't access local storage", e);
            syncResult.databaseError = true;
        }
    }


    abstract protected void prepare();

    abstract protected void queryCapabilities() throws IOException, HttpException, DavException, CalendarStorageException, ContactsStorageException;

    protected void processLocallyDeleted() throws CalendarStorageException, ContactsStorageException {
        // Remove locally deleted entries from server (if they have a name, i.e. if they were uploaded before),
        // but only if they don't have changed on the server. Then finally remove them from the local address book.
        LocalResource[] localList = localCollection.getDeleted();
        for (LocalResource local : localList) {
            final String fileName = local.getFileName();
            if (!TextUtils.isEmpty(fileName)) {
                Constants.log.info(fileName + " has been deleted locally -> deleting from server");
                try {
                    new DavResource(httpClient, collectionURL.newBuilder().addPathSegment(fileName).build())
                            .delete(local.getETag());
                } catch (IOException|HttpException e) {
                    Constants.log.warn("Couldn't delete " + fileName + " from server");
                }
            } else
                Constants.log.info("Removing local record #" + local.getId() + " which has been deleted locally and was never uploaded");
            local.delete();
            syncResult.stats.numDeletes++;
        }
    }

    protected void prepareDirty() throws CalendarStorageException, ContactsStorageException {
        // assign file names and UIDs to new contacts so that we can use the file name as an index
        for (LocalResource local : localCollection.getWithoutFileName()) {
            String uuid = UUID.randomUUID().toString();
            Constants.log.info("Found local record #" + local.getId() + " without file name; assigning file name/UID based on " + uuid);
            local.updateFileNameAndUID(uuid);
        }
    }

    abstract protected RequestBody prepareUpload(LocalResource resource) throws IOException, CalendarStorageException, ContactsStorageException;

    protected void uploadDirty() throws IOException, HttpException, CalendarStorageException, ContactsStorageException {
        // upload dirty contacts
        for (LocalResource local : localCollection.getDirty()) {
            final String fileName = local.getFileName();

            DavResource remote = new DavResource(httpClient, collectionURL.newBuilder().addPathSegment(fileName).build());

            // generate entity to upload (VCard, iCal, whatever)
            RequestBody body = prepareUpload(local);

            try {

                if (local.getETag() == null) {
                    Constants.log.info("Uploading new record " + fileName);
                    remote.put(body, null, true);
                    // TODO handle 30x
                } else {
                    Constants.log.info("Uploading locally modified record " + fileName);
                    remote.put(body, local.getETag(), false);
                    // TODO handle 30x
                }

            } catch (PreconditionFailedException e) {
                Constants.log.info("Resource has been modified on the server before upload, ignoring", e);
            }

            String eTag = null;
            GetETag newETag = (GetETag) remote.properties.get(GetETag.NAME);
            if (newETag != null) {
                eTag = newETag.eTag;
                Constants.log.debug("Received new ETag=" + eTag + " after uploading");
            } else
                Constants.log.debug("Didn't receive new ETag after uploading, setting to null");

            local.clearDirty(eTag);
        }
    }

    /**
     * Checks the current sync state (e.g. CTag) and whether synchronization from remote is required.
     * @return <ul>
     *      <li><code>true</code>   if the remote collection has changed, i.e. synchronization from remote is required</li>
     *      <li><code>false</code>  if the remote collection hasn't changed</li>
     * </ul>
     */
    protected boolean checkSyncState() throws CalendarStorageException, ContactsStorageException {
        // check CTag (ignore on manual sync)
        GetCTag getCTag = (GetCTag)davCollection.properties.get(GetCTag.NAME);
        if (getCTag != null)
            remoteCTag = getCTag.cTag;

        String localCTag = null;
        if (extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL))
            Constants.log.info("Manual sync, ignoring CTag");
        else
            localCTag = localCollection.getCTag();

        if (remoteCTag != null && remoteCTag.equals(localCTag)) {
            Constants.log.info("Remote collection didn't change (CTag=" + remoteCTag + "), no need to query children");
            return false;
        } else
            return true;
    }

    /**
     * Lists all local resources which should be taken into account for synchronization into {@link #localResources}.
     */
    protected void listLocal() throws CalendarStorageException, ContactsStorageException {
        // fetch list of local contacts and build hash table to index file name
        LocalResource[] localList = localCollection.getAll();
        localResources = new HashMap<>(localList.length);
        for (LocalResource resource : localList) {
            Constants.log.debug("Found local resource: " + resource.getFileName());
            localResources.put(resource.getFileName(), resource);
        }
    }

    /**
     * Lists all members of the remote collection which should be taken into account for synchronization into {@link #remoteResources}.
     */
    abstract protected void listRemote() throws IOException, HttpException, DavException;

    /**
     * Compares {@link #localResources} and {@link #remoteResources} by file name and ETag:
     * <ul>
     *     <li>Local resources which are not available in the remote collection (anymore) will be removed.</li>
     *     <li>Resources whose remote ETag has changed will be added into {@link #toDownload}</li>
     * </ul>
     */
    protected void compareLocalRemote() throws IOException, HttpException, DavException, CalendarStorageException, ContactsStorageException {
        /* check which contacts
           1. are not present anymore remotely -> delete immediately on local side
           2. updated remotely -> add to downloadNames
           3. added remotely  -> add to downloadNames
         */
        toDownload = new HashSet<>();
        for (String localName : localResources.keySet()) {
            DavResource remote = remoteResources.get(localName);
            if (remote == null) {
                Constants.log.info(localName + " is not on server anymore, deleting");
                localResources.get(localName).delete();
                syncResult.stats.numDeletes++;
            } else {
                // contact is still on server, check whether it has been updated remotely
                GetETag getETag = (GetETag) remote.properties.get(GetETag.NAME);
                if (getETag == null || getETag.eTag == null)
                    throw new DavException("Server didn't provide ETag");
                String localETag = localResources.get(localName).getETag(),
                        remoteETag = getETag.eTag;
                if (remoteETag.equals(localETag))
                    syncResult.stats.numSkippedEntries++;
                else {
                    Constants.log.info(localName + " has been changed on server (current ETag=" + remoteETag + ", last known ETag=" + localETag + ")");
                    toDownload.add(remote);
                }

                // remote entry has been seen, remove from list
                remoteResources.remove(localName);
            }
        }

        // add all unseen (= remotely added) remote contacts
        if (!remoteResources.isEmpty()) {
            Constants.log.info("New VCards have been found on the server: " + TextUtils.join(", ", remoteResources.keySet()));
            toDownload.addAll(remoteResources.values());
        }
    }

    /**
     * Downloads the remote resources in {@link #toDownload} and stores them locally.
     */
    abstract protected void downloadRemote() throws IOException, HttpException, DavException, ContactsStorageException, CalendarStorageException;

    protected void saveSyncState() throws CalendarStorageException, ContactsStorageException {
        /* Save sync state (CTag). It doesn't matter if it has changed during the sync process
           (for instance, because another client has uploaded changes), because this will simply
           cause all remote entries to be listed at the next sync. */
        Constants.log.info("Saving CTag=" + remoteCTag);
        localCollection.setCTag(remoteCTag);
    }

}
