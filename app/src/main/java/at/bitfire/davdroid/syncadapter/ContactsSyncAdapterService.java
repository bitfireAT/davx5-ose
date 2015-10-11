/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.ResponseBody;

import org.apache.commons.io.Charsets;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import at.bitfire.dav4android.DavAddressBook;
import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.GetETag;
import at.bitfire.dav4android.property.SupportedAddressData;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.davdroid.resource.LocalContact;
import at.bitfire.vcard4android.Contact;
import ezvcard.VCardVersion;
import ezvcard.property.Uid;

public class ContactsSyncAdapterService extends Service {
	private static ContactsSyncAdapter syncAdapter;

	@Override
	public void onCreate() {
		if (syncAdapter == null)
			syncAdapter = new ContactsSyncAdapter(getApplicationContext());
	}

	@Override
	public void onDestroy() {
		syncAdapter = null;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return syncAdapter.getSyncAdapterBinder();
	}
	

	private static class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {

        public ContactsSyncAdapter(Context context) {
            super(context, false);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            Constants.log.info("Starting sync for authority " + authority);

            AccountSettings settings = new AccountSettings(getContext(), account);
            HttpClient httpClient = new HttpClient(settings.getUserName(), settings.getPassword(), settings.getPreemptiveAuth());

            HttpUrl addressBookURL = HttpUrl.parse(settings.getAddressBookURL());
            DavAddressBook dav = new DavAddressBook(httpClient, addressBookURL);
            try {
                // prepare local address book
                LocalAddressBook addressBook = new LocalAddressBook(account, provider);

                // prepare remote address book
                boolean hasVCard4 = false;
                dav.propfind(0, SupportedAddressData.NAME);
                SupportedAddressData supportedAddressData = (SupportedAddressData)dav.properties.get(SupportedAddressData.NAME);
                if (supportedAddressData != null)
                    for (MediaType type : supportedAddressData.types)
                        if ("text/vcard; version=4.0".equalsIgnoreCase(type.toString()))
                            hasVCard4 = true;
                Constants.log.info("Server advertises VCard/4 support: " + hasVCard4);

                // Remove locally deleted contacts from server (if they have a name, i.e. if they were uploaded before),
                // but only if they don't have changed on the server. Then finally remove them from the local address book.
                LocalContact[] localList = addressBook.getDeleted();
                for (LocalContact local : localList) {
                    final String fileName = local.getFileName();
                    if (!TextUtils.isEmpty(fileName)) {
                        Constants.log.info(fileName + " has been deleted locally -> deleting from server");
                        try {
                            new DavResource(httpClient, addressBookURL.newBuilder().addPathSegment(fileName).build())
                                .delete(local.eTag);
                        } catch(IOException|HttpException e) {
                            Constants.log.warn("Couldn't delete " + fileName + " from server");
                        }
                    } else
                        Constants.log.info("Removing local contact #" + local.getId() + " which has been deleted locally and was never uploaded");
                    local.delete();
                }

                // assign file names and UIDs to new contacts so that we can use the file name as an index
                localList = addressBook.getWithoutFileName();
                for (LocalContact local : localList) {
                    String uuid = Uid.random().toString();
                    Constants.log.info("Found local contact #" + local.getId() + " without file name; assigning name UID/name " + uuid + "[.vcf]");
                    local.updateUID(uuid);
                }

                // upload dirty contacts
                localList = addressBook.getDirty();
                for (LocalContact local : localList) {
                    final String fileName = local.getFileName();

                    DavResource remote = new DavResource(httpClient, addressBookURL.newBuilder().addPathSegment(fileName).build());

                    RequestBody vCard = RequestBody.create(
                            hasVCard4 ? DavAddressBook.MIME_VCARD4 : DavAddressBook.MIME_VCARD3_UTF8,
                            local.getContact().toStream(hasVCard4 ? VCardVersion.V4_0 : VCardVersion.V3_0).toByteArray()
                    );

                    if (local.eTag == null) {
                        Constants.log.info("Uploading new contact " + fileName);
                        remote.put(vCard, null, local.eTag);
                    } else {
                        Constants.log.info("Uploading locally modified contact " + fileName);
                        remote.put(vCard, local.eTag, null);
                    }

                    // reset DIRTY
                }

                // check CTag (ignore on forced sync)
                if (true) {
                    // fetch list of local contacts and build hash table to index file name
                    localList = addressBook.getAll();
                    Map<String, LocalContact> localContacts = new HashMap<>(localList.length);
                    for (LocalContact contact : localList) {
                        Constants.log.debug("Found local contact: " + contact.getFileName());
                        localContacts.put(contact.getFileName(), contact);
                    }

                    // fetch list of remote VCards and build hash table to index file name
                    Constants.log.info("Listing remote VCards");
                    dav.queryMemberETags();
                    Map<String, DavResource> remoteContacts = new HashMap<>(dav.members.size());
                    for (DavResource vCard : dav.members) {
                        String fileName = vCard.fileName();
                        Constants.log.debug("Found remote VCard: " + fileName);
                        remoteContacts.put(fileName, vCard);
                    }

                    /* check which contacts
                       1. are not present anymore remotely -> delete immediately on local side
                       2. updated remotely -> add to downloadNames
                       3. added remotely  -> add to downloadNames
                     */
                    Set<DavResource> toDownload = new HashSet<>();
                    for (String localName : localContacts.keySet()) {
                        DavResource remote = remoteContacts.get(localName);
                        if (remote == null) {
                            Constants.log.info(localName + " is not on server anymore, deleting");
                            localContacts.get(localName).delete();
                        } else {
                            // contact is still on server, check whether it has been updated remotely
                            GetETag getETag = (GetETag)remote.properties.get(GetETag.NAME);
                            if (getETag == null || getETag.eTag == null)
                                throw new DavException("Server didn't provide ETag");
                            String  localETag = localContacts.get(localName).eTag,
                                    remoteETag = getETag.eTag;
                            if (!remoteETag.equals(localETag)) {
                                Constants.log.info(localName + " has been changed on server (current ETag=" + remoteETag + ", last known ETag=" + localETag + ")");
                                toDownload.add(remote);
                            }

                            // remote entry has been seen, remove from list
                            remoteContacts.remove(localName);
                        }
                    }

                    // add all unseen (= remotely added) remote contacts
                    if (!remoteContacts.isEmpty()) {
                        Constants.log.info("New VCards have been found on the server: " + TextUtils.join(", ", remoteContacts.keySet()));
                        toDownload.addAll(remoteContacts.values());
                    }

                    // download new/updated VCards from server
                    for (DavResource remoteContact : toDownload) {
                        Constants.log.info("Downloading " + remoteContact.location);
                        String fileName = remoteContact.fileName();

                        ResponseBody body = remoteContact.get("text/vcard;q=0.5, text/vcard;charset=utf-8;q=0.8, text/vcard;version=4.0");
                        String remoteETag = ((GetETag)remoteContact.properties.get(GetETag.NAME)).eTag;

                        Contact contacts[] = Contact.fromStream(body.byteStream(), body.contentType().charset(Charsets.UTF_8));
                        if (contacts.length == 1) {
                            Contact newData = contacts[0];

                            // delete local contact, if it exists
                            LocalContact localContact = localContacts.get(fileName);
                            if (localContact != null) {
                                Constants.log.info("Updating " + fileName + " in local address book");
                                localContact.eTag = remoteETag;
                                localContact.update(newData);
                            } else {
                                Constants.log.info("Adding " + fileName + " to local address book");
                                localContact = new LocalContact(addressBook, newData, fileName, remoteETag);
                                localContact.add();
                            }

                            // add the new contact
                        } else
                            Constants.log.error("Received VCard with not exactly one VCARD, ignoring " + fileName);
                    }
                }

            } catch (Exception e) {
                Log.e("davdroid", "querying member etags", e);
            }

            Constants.log.info("Sync complete for authority " + authority);
        }
    }
}
