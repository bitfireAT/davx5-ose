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
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.apache.commons.codec.Charsets;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import at.bitfire.dav4android.DavAddressBook;
import at.bitfire.dav4android.DavResource;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.property.AddressData;
import at.bitfire.dav4android.property.GetCTag;
import at.bitfire.dav4android.property.GetContentType;
import at.bitfire.dav4android.property.GetETag;
import at.bitfire.dav4android.property.ResourceType;
import at.bitfire.dav4android.property.SupportedAddressData;
import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.ArrayUtils;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.HttpClient;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.davdroid.resource.LocalContact;
import at.bitfire.davdroid.resource.LocalGroup;
import at.bitfire.davdroid.resource.LocalResource;
import at.bitfire.ical4android.CalendarStorageException;
import at.bitfire.vcard4android.BatchOperation;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;
import at.bitfire.vcard4android.GroupMethod;
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

/**
 * <p>Synchronization manager for CardDAV collections; handles contacts and groups.</p>
 *
 * <p></p>Group handling differs according to the {@link #groupMethod}. There are two basic methods to
 * handle/manage groups:</p>
 * <ul>
 *     <li>{@code CATEGORIES}: groups memberships are attached to each contact and represented as
 *     "category". When a group is dirty or has been deleted, all its members have to be set to
 *     dirty, too (because they have to be uploaded without the respective category). This
 *     is done in {@link #prepareDirty()}. Empty groups can be deleted without further processing,
 *     which is done in {@link #postProcess()} because groups may become empty after downloading
 *     updated remoted contacts.</li>
 *     <li>Groups as separate VCards: individual and group contacts (with a list of member UIDs) are
 *     distinguished. When a local group is dirty, its members don't need to be set to dirty.
 *     <ol>
 *         <li>However, when a contact is dirty, it has
 *         to be checked whether its group memberships have changed. In this case, the respective
 *         groups have to be set to dirty. For instance, if contact A is in group G and H, and then
 *         group membership of G is removed, the contact will be set to dirty because of the changed
 *         {@link android.provider.ContactsContract.CommonDataKinds.GroupMembership}. DAVdroid will
 *         then have to check whether the group memberships have actually changed, and if so,
 *         all affected groups have to be set to dirty. To detect changes in group memberships,
 *         DAVdroid always mirrors all {@link android.provider.ContactsContract.CommonDataKinds.GroupMembership}
 *         data rows in respective {@link at.bitfire.vcard4android.CachedGroupMembership} rows.
 *         If the cached group memberships are not the same as the current group member ships, the
 *         difference set (in our example G, because its in the cached memberships, but not in the
 *         actual ones) is marked as dirty. This is done in {@link #prepareDirty()}.</li>
 *         <li>When downloading remote contacts, groups (+ member information) may be received
 *         by the actual members. Thus, the member lists have to be cached until all VCards
 *         are received. This is done by caching the member UIDs of each group in
 *         {@link LocalGroup#COLUMN_PENDING_MEMBERS}. In {@link #postProcess()},
 *         these "pending memberships" are assigned to the actual contacs and then cleaned up.</li>
 *     </ol>
 * </ul>
 */
public class ContactsSyncManager extends SyncManager {
    protected static final int MAX_MULTIGET = 10;

    final private ContentProviderClient provider;
    final private CollectionInfo remote;

    private boolean hasVCard4;
    private GroupMethod groupMethod;


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
        ContentValues values = new ContentValues(2);
        values.put(ContactsContract.Settings.SHOULD_SYNC, 1);
        values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1);
        localAddressBook.updateSettings(values);

        collectionURL = HttpUrl.parse(url);
        davCollection = new DavAddressBook(httpClient, collectionURL);
    }

    @Override
    protected void queryCapabilities() throws DavException, IOException, HttpException {
        // prepare remote address book
        davCollection.propfind(0, SupportedAddressData.NAME, GetCTag.NAME);
        SupportedAddressData supportedAddressData = (SupportedAddressData)davCollection.properties.get(SupportedAddressData.NAME);
        hasVCard4 = supportedAddressData != null && supportedAddressData.hasVCard4();
        App.log.info("Server advertises VCard/4 support: " + hasVCard4);

        groupMethod = settings.getGroupMethod();
        App.log.info("Contact group method: " + groupMethod);

        localAddressBook().includeGroups = groupMethod == GroupMethod.GROUP_VCARDS;
    }

    @Override
    protected void prepareDirty() throws CalendarStorageException, ContactsStorageException {
        super.prepareDirty();

        LocalAddressBook addressBook = localAddressBook();

        if (groupMethod == GroupMethod.CATEGORIES) {
            /* groups memberships are represented as contact CATEGORIES */

            // groups with DELETED=1: set all members to dirty, then remove group
            for (LocalGroup group : addressBook.getDeletedGroups()) {
                App.log.fine("Finally removing group " + group);
                // useless because Android deletes group memberships as soon as a group is set to DELETED:
                // group.markMembersDirty();
                group.delete();
            }

            // groups with DIRTY=1: mark all memberships as dirty, then clean DIRTY flag of group
            for (LocalGroup group : addressBook.getDirtyGroups()) {
                App.log.fine("Marking members of modified group " + group + " as dirty");
                group.markMembersDirty();
                group.clearDirty(null);
            }
        } else {
            /* groups as separate VCards: there are group contacts and individual contacts */

            // mark groups with changed members as dirty
            BatchOperation batch = new BatchOperation(addressBook.provider);
            for (LocalContact contact : addressBook.getDirtyContacts())
                try {
                    App.log.fine("Looking for changed group memberships of contact " + contact.getFileName());
                    Set<Long>   cachedGroups = contact.getCachedGroupMemberships(),
                                currentGroups = contact.getGroupMemberships();
                    for (Long groupID : SetUtils.disjunction(cachedGroups, currentGroups)) {
                        App.log.fine("Marking group as dirty: " + groupID);
                        batch.enqueue(new BatchOperation.Operation(
                                ContentProviderOperation.newUpdate(addressBook.syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, groupID)))
                                .withValue(Groups.DIRTY, 1)
                                .withYieldAllowed(true)
                        ));
                    }
                } catch(FileNotFoundException ignored) {
                }
            batch.commit();
        }
    }

    @Override
    protected RequestBody prepareUpload(@NonNull LocalResource resource) throws IOException, ContactsStorageException {
        final Contact contact;
        if (resource instanceof LocalContact) {
            LocalContact local = ((LocalContact)resource);
            contact = local.getContact();

            if (groupMethod == GroupMethod.CATEGORIES) {
                // add groups as CATEGORIES
                for (long groupID : local.getGroupMemberships()) {
                    try {
                        @Cleanup Cursor c = provider.query(
                                localAddressBook().syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, groupID)),
                                new String[] { Groups.TITLE },
                                null, null,
                                null
                        );
                        if (c != null && c.moveToNext()) {
                            String title = c.getString(0);
                            if (!TextUtils.isEmpty(title))
                                contact.categories.add(title);
                        }
                    } catch(RemoteException e) {
                        throw new ContactsStorageException("Couldn't find group for adding CATEGORIES", e);
                    }
                }
            }
        } else if (resource instanceof LocalGroup)
            contact = ((LocalGroup)resource).getContact();
        else
            throw new IllegalArgumentException("Argument must be LocalContact or LocalGroup");

        App.log.log(Level.FINE, "Preparing upload of VCard " + resource.getFileName(), contact);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        contact.write(hasVCard4 ? VCardVersion.V4_0 : VCardVersion.V3_0, groupMethod, settings.getVCardRFC6868(), os);

        return RequestBody.create(
                hasVCard4 ? DavAddressBook.MIME_VCARD4 : DavAddressBook.MIME_VCARD3_UTF8,
                os.toByteArray()
        );
    }

    @Override
    protected void listRemote() throws IOException, HttpException, DavException {
        // fetch list of remote VCards and build hash table to index file name
        davAddressBook().propfind(1, ResourceType.NAME, GetETag.NAME);

        remoteResources = new HashMap<>(davCollection.members.size());
        for (DavResource vCard : davCollection.members) {
            // ignore member collections
            ResourceType type = (ResourceType)vCard.properties.get(ResourceType.NAME);
            if (type != null && type.types.contains(ResourceType.COLLECTION))
                continue;

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

                // CardDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc6352#section-6.3.2.3]
                GetETag eTag = (GetETag)remote.properties.get(GetETag.NAME);
                if (eTag == null || StringUtils.isEmpty(eTag.eTag))
                    throw new DavException("Received CardDAV GET response without ETag for " + remote.location);

                Charset charset = Charsets.UTF_8;
                MediaType contentType = body.contentType();
                if (contentType != null)
                    charset = contentType.charset(Charsets.UTF_8);

                @Cleanup InputStream stream = body.byteStream();
                processVCard(remote.fileName(), eTag.eTag, stream, charset, downloader);

            } else {
                // multiple contacts, use multi-get
                List<HttpUrl> urls = new LinkedList<>();
                for (DavResource remote : bunch)
                    urls.add(remote.location);
                davAddressBook().multiget(urls.toArray(new HttpUrl[urls.size()]), hasVCard4);

                // process multiget results
                for (DavResource remote : davCollection.members) {
                    String eTag;
                    GetETag getETag = (GetETag)remote.properties.get(GetETag.NAME);
                    if (getETag != null)
                        eTag = getETag.eTag;
                    else
                        throw new DavException("Received multi-get response without ETag");

                    Charset charset = Charsets.UTF_8;
                    GetContentType getContentType = (GetContentType)remote.properties.get(GetContentType.NAME);
                    if (getContentType != null && getContentType.type != null) {
                        MediaType type = MediaType.parse(getContentType.type);
                        if (type != null)
                            charset = type.charset(Charsets.UTF_8);
                    }

                    AddressData addressData = (AddressData)remote.properties.get(AddressData.NAME);
                    if (addressData == null || addressData.vCard == null)
                        throw new DavException("Received multi-get response without address data");

                    @Cleanup InputStream stream = new ByteArrayInputStream(addressData.vCard.getBytes());
                    processVCard(remote.fileName(), eTag, stream, charset, downloader);
                }
            }
        }
    }

    @Override
    protected void postProcess() throws CalendarStorageException, ContactsStorageException {
        if (groupMethod == GroupMethod.CATEGORIES) {
            /* VCard3 group handling: groups memberships are represented as contact CATEGORIES */

            // remove empty groups
            App.log.info("Removing empty groups");
            localAddressBook().removeEmptyGroups();

        } else {
            /* VCard4 group handling: there are group contacts and individual contacts */
            App.log.info("Assigning memberships of downloaded contact groups");
            LocalGroup.applyPendingMemberships(localAddressBook());
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

    private void processVCard(String fileName, String eTag, InputStream stream, Charset charset, Contact.Downloader downloader) throws IOException, ContactsStorageException {
        App.log.info("Processing CardDAV resource " + fileName);
        Contact[] contacts = Contact.fromStream(stream, charset, downloader);
        if (contacts.length == 0) {
            App.log.warning("Received VCard without data, ignoring");
            return;
        } else if (contacts.length > 1)
            App.log.warning("Received multiple VCards, using first one");

        final Contact newData = contacts[0];

        if (groupMethod == GroupMethod.CATEGORIES && newData.group) {
            groupMethod = GroupMethod.GROUP_VCARDS;
            App.log.warning("Received group VCard although group method is CATEGORIES. Deleting all groups; new group method: " + groupMethod);
            localAddressBook().removeGroups();
            settings.setGroupMethod(groupMethod);
        }

        // update local contact, if it exists
        LocalResource local = localResources.get(fileName);
        if (local != null) {
            App.log.log(Level.INFO, "Updating " + fileName + " in local address book", newData);

            if (local instanceof LocalGroup && newData.group) {
                // update group
                LocalGroup group = (LocalGroup)local;
                group.eTag = eTag;
                group.updateFromServer(newData);
                syncResult.stats.numUpdates++;

            } else if (local instanceof LocalContact && !newData.group) {
                // update contact
                LocalContact contact = (LocalContact)local;
                contact.eTag = eTag;
                contact.update(newData);
                syncResult.stats.numUpdates++;

            } else {
                // group has become an individual contact or vice versa
                try {
                    local.delete();
                    local = null;
                } catch(CalendarStorageException e) {
                    // CalendarStorageException is not used by LocalGroup and LocalContact
                }
            }
        }

        if (local == null) {
            if (newData.group) {
                App.log.log(Level.INFO, "Creating local group", newData);
                LocalGroup group = new LocalGroup(localAddressBook(), newData, fileName, eTag);
                group.create();

                local = group;
            } else {
                App.log.log(Level.INFO, "Creating local contact", newData);
                LocalContact contact = new LocalContact(localAddressBook(), newData, fileName, eTag);
                contact.create();

                local = contact;
            }
            syncResult.stats.numInserts++;
        }

        if (groupMethod == GroupMethod.CATEGORIES && local instanceof LocalContact) {
            // VCard3: update group memberships from CATEGORIES
            LocalContact contact = (LocalContact)local;

            BatchOperation batch = new BatchOperation(provider);
            contact.removeGroupMemberships(batch);

            for (String category : contact.getContact().categories) {
                long groupID = localAddressBook().findOrCreateGroup(category);
                contact.addToGroup(batch, groupID);
            }

            batch.commit();
        }
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

            OkHttpClient resourceClient = HttpClient.create(context);

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
