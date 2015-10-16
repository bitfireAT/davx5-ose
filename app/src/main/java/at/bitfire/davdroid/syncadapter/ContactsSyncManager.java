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
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import at.bitfire.dav4android.DavAddressBook;
import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.AddressData;
import at.bitfire.dav4android.property.GetCTag;
import at.bitfire.dav4android.property.GetContentType;
import at.bitfire.dav4android.property.GetETag;
import at.bitfire.dav4android.property.SupportedAddressData;
import at.bitfire.davdroid.ArrayUtils;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.davdroid.resource.LocalContact;
import at.bitfire.davdroid.resource.LocalResource;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;
import ezvcard.VCardVersion;
import ezvcard.util.IOUtils;
import lombok.Cleanup;
import lombok.RequiredArgsConstructor;

public class ContactsSyncManager extends SyncManager {
    protected static final int MAX_MULTIGET = 10;

    final protected ContentProviderClient provider;
    protected boolean hasVCard4;


    public ContactsSyncManager(Context context, Account account, Bundle extras, ContentProviderClient provider, SyncResult result) {
        super(Constants.NOTIFICATION_CONTACTS_SYNC, context, account, extras, result);
        this.provider = provider;
    }


    @Override
    protected void prepare() throws ContactsStorageException {
        // prepare local address book
        localCollection = new LocalAddressBook(account, provider);

        String url = localAddressBook().getURL();
        if (url == null)
            throw new ContactsStorageException("Couldn't get address book URL");
        collectionURL = HttpUrl.parse(url);
        davCollection = new DavAddressBook(httpClient, collectionURL);
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
        Constants.log.info("Server advertises VCard/4 support: " + hasVCard4);
    }

    @Override
    protected RequestBody prepareUpload(LocalResource resource) throws IOException, ContactsStorageException {
        LocalContact local = (LocalContact)resource;
        return RequestBody.create(
                hasVCard4 ? DavAddressBook.MIME_VCARD4 : DavAddressBook.MIME_VCARD3_UTF8,
                local.getContact().toStream(hasVCard4 ? VCardVersion.V4_0 : VCardVersion.V3_0).toByteArray()
        );
    }

    @Override
    protected void listRemote() throws IOException, HttpException, DavException {
        // fetch list of remote VCards and build hash table to index file name

        try {
            davAddressBook().addressbookQuery();
        } catch(HttpException e) {
            if (e.status/100 == 4) {
                Constants.log.warn("Server error on REPORT addressbook query, falling back to PROPFIND", e);
                davAddressBook().propfind(1, GetETag.NAME);
            }
        }

        remoteResources = new HashMap<>(davCollection.members.size());
        for (DavResource vCard : davCollection.members) {
            String fileName = vCard.fileName();
            Constants.log.debug("Found remote VCard: " + fileName);
            remoteResources.put(fileName, vCard);
        }
    }

    @Override
    protected void downloadRemote() throws IOException, HttpException, DavException, ContactsStorageException {
        Constants.log.info("Downloading " + toDownload.size() + " contacts (" + MAX_MULTIGET + " at once)");

        // prepare downloader which may be used to download external resource like contact photos
        Contact.Downloader downloader = new ResourceDownloader(httpClient, collectionURL);

        // download new/updated VCards from server
        for (DavResource[] bunch : ArrayUtils.partition(toDownload.toArray(new DavResource[toDownload.size()]), MAX_MULTIGET)) {
            Constants.log.info("Downloading " + StringUtils.join(bunch, ", "));

            if (bunch.length == 1) {
                // only one contact, use GET
                DavResource remote = bunch[0];

                ResponseBody body = remote.get("text/vcard;q=0.5, text/vcard;charset=utf-8;q=0.8, text/vcard;version=4.0");
                String eTag = ((GetETag) remote.properties.get(GetETag.NAME)).eTag;

                @Cleanup InputStream stream = body.byteStream();
                processVCard(remote.fileName(), eTag, stream, body.contentType().charset(Charsets.UTF_8), downloader);

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


    // helpers

    private DavAddressBook davAddressBook() { return (DavAddressBook)davCollection; }
    private LocalAddressBook localAddressBook() { return (LocalAddressBook)localCollection; }

    private void processVCard(String fileName, String eTag, InputStream stream, Charset charset, Contact.Downloader downloader) throws IOException, ContactsStorageException {
        Contact contacts[] = Contact.fromStream(stream, charset, downloader);
        if (contacts != null && contacts.length == 1) {
            Contact newData = contacts[0];

            // update local contact, if it exists
            LocalContact localContact = (LocalContact)localResources.get(fileName);
            if (localContact != null) {
                Constants.log.info("Updating " + fileName + " in local address book");
                localContact.eTag = eTag;
                localContact.update(newData);
                syncResult.stats.numUpdates++;
            } else {
                Constants.log.info("Adding " + fileName + " to local address book");
                localContact = new LocalContact(localAddressBook(), newData, fileName, eTag);
                localContact.add();
                syncResult.stats.numInserts++;
            }
        } else
            Constants.log.error("Received VCard with not exactly one VCARD, ignoring " + fileName);
    }


    // downloader helper class

    @RequiredArgsConstructor
    private static class ResourceDownloader implements Contact.Downloader {
        final HttpClient httpClient;
        final HttpUrl baseUrl;

        @Override
        public byte[] download(String url, String accepts) {
            HttpUrl httpUrl = HttpUrl.parse(url);
            HttpClient resourceClient = new HttpClient(httpClient, httpUrl.host());
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
                        Constants.log.error("Couldn't download external resource");
                }
            } catch(IOException e) {
                Constants.log.error("Couldn't download external resource", e);
            }
            return null;
        }
    }

}
