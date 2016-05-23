/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.provider.ContactsContract;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import at.bitfire.dav4android.DavAddressBook;
import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.AddressData;
import at.bitfire.dav4android.property.GetCTag;
import at.bitfire.dav4android.property.GetContentType;
import at.bitfire.dav4android.property.GetETag;
import at.bitfire.dav4android.property.SupportedAddressData;
import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.ArrayUtils;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.DavUtils;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.davdroid.resource.LocalContact;
import at.bitfire.davdroid.resource.LocalGroup;
import at.bitfire.davdroid.resource.LocalResource;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;
import ezvcard.VCardVersion;
import ezvcard.util.IOUtils;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ContactsSyncManager extends SyncManager {
    protected static final int MAX_MULTIGET = 10;

    final private ContentProviderClient provider;
    final private CollectionInfo remote;
    private boolean hasVCard4;


    public ContactsSyncManager(Context context, Account account, AccountSettings settings, Bundle extras, String authority, ContentProviderClient provider, SyncResult result, CollectionInfo remote) throws InvalidAccountException {
        super(context, account, settings, extras, authority, result, "addressBook");
        this.provider = provider;
        this.remote = remote;
    }

    @Override
    protected int notificationId() {
        return Constants.NOTIFICATION_CONTACTS_SYNC;
    }

    @Override
    protected String getSyncErrorTitle() {
        return context.getString(R.string.sync_error_contacts, account.name);
    }


    @Override
    protected void prepare() throws ContactsStorageException {
        // prepare local address book
        localCollection = new LocalAddressBook(account, provider);
        LocalAddressBook localAddressBook = localAddressBook();

        String url = remote.url;
        String lastUrl = localAddressBook.getURL();
        if (!url.equals(lastUrl)) {
            App.log.info("Selected address book has changed from " + lastUrl + " to " + url + ", deleting all local contacts");
            localAddressBook.deleteAll();
        }

        // set up Contacts Provider Settings
        ContentValues settings = new ContentValues(2);
        settings.put(ContactsContract.Settings.SHOULD_SYNC, 1);
        settings.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1);
        localAddressBook.updateSettings(settings);

        collectionURL = HttpUrl.parse(url);
        davCollection = new DavAddressBook(httpClient, collectionURL);

        processChangedGroups();
    }

    @Override
    protected void queryCapabilities() throws DavException, IOException, HttpException {
        // prepare remote address book
        hasVCard4 = false;
        davCollection.propfind(0, SupportedAddressData.NAME, GetCTag.NAME);
        SupportedAddressData supportedAddressData = (SupportedAddressData) davCollection.properties.get(SupportedAddressData.NAME);
        if (supportedAddressData != null)
            for (MediaType type : supportedAddressData.types)
                if ("text/vcard; version=4.0".equalsIgnoreCase(type.toString()))
                    hasVCard4 = true;
        App.log.info("Server advertises VCard/4 support: " + hasVCard4);
    }

    @Override
    protected RequestBody prepareUpload(LocalResource resource) throws IOException, ContactsStorageException {
        LocalContact local = (LocalContact)resource;
        App.log.log(Level.FINE, "Preparing upload of contact " + local.getFileName(), new Object[] { local.getContact() });
        return DavUtils.requestBody(
                hasVCard4 ? DavAddressBook.MIME_VCARD4 : DavAddressBook.MIME_VCARD3_UTF8,
                local.getContact().toStream(hasVCard4 ? VCardVersion.V4_0 : VCardVersion.V3_0)
        );
    }

    @Override
    protected void listRemote() throws IOException, HttpException, DavException {
        // fetch list of remote VCards and build hash table to index file name

        try {
            davAddressBook().addressbookQuery();
        } catch(HttpException e) {
            /*  non-successful responses to CARDDAV:addressbook-query with empty filter, tested on 2015/10/21
             *  fastmail.com                 403 Forbidden (DAV:error CARDDAV:supported-filter)
             *  mailbox.org (OpenXchange)    400 Bad Request
             *  SOGo                         207 Multi-status, but without entries       http://www.sogo.nu/bugs/view.php?id=3370
             *  Zimbra ZCS                   500 Server Error                            https://bugzilla.zimbra.com/show_bug.cgi?id=101902
             */
            if (e.status == 400 || e.status == 403 || e.status == 500 || e.status == 501) {
                App.log.log(Level.WARNING, "Server error on REPORT addressbook-query, falling back to PROPFIND", e);
                davAddressBook().propfind(1, GetETag.NAME);
            } else
                // no defined fallback, pass through exception
                throw e;
        }

        remoteResources = new HashMap<>(davCollection.members.size());
        for (DavResource vCard : davCollection.members) {
            String fileName = vCard.fileName();
            App.log.fine("Found remote VCard: " + fileName);
            remoteResources.put(fileName, vCard);
        }
    }

    @Override
    protected void downloadRemote() throws IOException, HttpException, DavException, ContactsStorageException {
        App.log.info("Downloading " + toDownload.size() + " contacts (" + MAX_MULTIGET + " at once)");

        // prepare downloader which may be used to download external resource like contact photos
        Contact.Downloader downloader = new ResourceDownloader(collectionURL);

        // download new/updated VCards from server
        for (DavResource[] bunch : ArrayUtils.partition(toDownload.toArray(new DavResource[toDownload.size()]), MAX_MULTIGET)) {
            if (Thread.interrupted())
                return;

            App.log.info("Downloading " + StringUtils.join(bunch, ", "));

            if (bunch.length == 1) {
                // only one contact, use GET
                DavResource remote = bunch[0];

                ResponseBody body = remote.get("text/vcard;version=4.0, text/vcard;charset=utf-8;q=0.8, text/vcard;q=0.5");
                String eTag = ((GetETag) remote.properties.get(GetETag.NAME)).eTag;

                Charset charset = Charsets.UTF_8;
                MediaType contentType = body.contentType();
                if (contentType != null)
                    charset = contentType.charset(Charsets.UTF_8);

                @Cleanup InputStream stream = body.byteStream();
                processVCard(remote.fileName(), eTag, stream, charset, downloader);

            } else {
                // multiple contacts, use multi-get
                List<HttpUrl> urls = new LinkedList<>();
                for (DavResource remote : bunch)
                    urls.add(remote.location);
                davAddressBook().multiget(urls.toArray(new HttpUrl[urls.size()]), hasVCard4);

                // process multiget results
                for (DavResource remote : davCollection.members) {
                    String eTag;
                    GetETag getETag = (GetETag) remote.properties.get(GetETag.NAME);
                    if (getETag != null)
                        eTag = getETag.eTag;
                    else
                        throw new DavException("Received multi-get response without ETag");

                    Charset charset = Charsets.UTF_8;
                    GetContentType getContentType = (GetContentType) remote.properties.get(GetContentType.NAME);
                    if (getContentType != null && getContentType.type != null) {
                        MediaType type = MediaType.parse(getContentType.type);
                        if (type != null)
                            charset = type.charset(Charsets.UTF_8);
                    }

                    AddressData addressData = (AddressData) remote.properties.get(AddressData.NAME);
                    if (addressData == null || addressData.vCard == null)
                        throw new DavException("Received multi-get response without address data");

                    @Cleanup InputStream stream = new ByteArrayInputStream(addressData.vCard.getBytes());
                    processVCard(remote.fileName(), eTag, stream, charset, downloader);
                }
            }
        }
    }

    @Override
    protected void saveSyncState() throws CalendarStorageException, ContactsStorageException {
        super.saveSyncState();
        ((LocalAddressBook)localCollection).setURL(remote.url);
    }


    // helpers

    private DavAddressBook davAddressBook() { return (DavAddressBook)davCollection; }
    private LocalAddressBook localAddressBook() { return (LocalAddressBook)localCollection; }

    private void processChangedGroups() throws ContactsStorageException {
        LocalAddressBook addressBook = localAddressBook();

        // groups with DELETED=1: remove group finally
        for (LocalGroup group : addressBook.getDeletedGroups()) {
            long groupId = group.getId();
            App.log.fine("Finally removing group #" + groupId);
            // remove group memberships, but not as sync adapter (should marks contacts as DIRTY)
            // NOTE: doesn't work that way because Contact Provider removes the group memberships even for DELETED groups
            // addressBook.removeGroupMemberships(groupId, false);
            group.delete();
        }

        // groups with DIRTY=1: mark all memberships as dirty, then clean DIRTY flag of group
        for (LocalGroup group : addressBook.getDirtyGroups()) {
            long groupId = group.getId();
            App.log.fine("Marking members of modified group #" + groupId + " as dirty");
            addressBook.markMembersDirty(groupId);
            group.clearDirty();
        }
    }

    private void processVCard(String fileName, String eTag, InputStream stream, Charset charset, Contact.Downloader downloader) throws IOException, ContactsStorageException {
        Contact[] contacts = Contact.fromStream(stream, charset, downloader);
        if (contacts.length == 1) {
            Contact newData = contacts[0];

            // update local contact, if it exists
            LocalContact localContact = (LocalContact)localResources.get(fileName);
            if (localContact != null) {
                App.log.info("Updating " + fileName + " in local address book");
                localContact.eTag = eTag;
                localContact.update(newData);
                syncResult.stats.numUpdates++;
            } else {
                App.log.info("Adding " + fileName + " to local address book");
                localContact = new LocalContact(localAddressBook(), newData, fileName, eTag);
                localContact.add();
                syncResult.stats.numInserts++;
            }
        } else
            App.log.severe("Received VCard with not exactly one VCARD, ignoring " + fileName);
    }


    // downloader helper class

    @RequiredArgsConstructor
    private class ResourceDownloader implements Contact.Downloader {
        final HttpUrl baseUrl;

        @Override
        public byte[] download(String url, String accepts) {
            HttpUrl httpUrl = HttpUrl.parse(url);

            if (httpUrl == null) {
                App.log.log(Level.SEVERE, "Invalid external resource URL", url);
                return null;
            }

            String host = httpUrl.host();
            if (host == null) {
                App.log.log(Level.SEVERE, "External resource URL doesn't specify a host name", url);
                return null;
            }

            OkHttpClient resourceClient = HttpClient.create();

            // authenticate only against a certain host, and only upon request
            resourceClient = HttpClient.addAuthentication(resourceClient, baseUrl.host(), settings.username(), settings.password());

            // allow redirects
            resourceClient = resourceClient.newBuilder()
                    .followRedirects(true)
                    .build();

            try {
                Response response = resourceClient.newCall(new Request.Builder()
                        .get()
                        .url(httpUrl)
                        .build()).execute();

                ResponseBody body = response.body();
                if (body != null) {
                    @Cleanup InputStream stream = body.byteStream();
                    if (response.isSuccessful() && stream != null) {
                        return IOUtils.toByteArray(stream);
                    } else
                        App.log.severe("Couldn't download external resource");
                }
            } catch(IOException e) {
                App.log.log(Level.SEVERE, "Couldn't download external resource", e);
            }
            return null;
        }
    }

}
