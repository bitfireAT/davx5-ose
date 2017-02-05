/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.support.annotation.NonNull;

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.vcard4android.AndroidAddressBook;
import at.bitfire.vcard4android.AndroidContact;
import at.bitfire.vcard4android.AndroidGroup;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;


public class LocalAddressBook extends AndroidAddressBook implements LocalCollection {

    protected static final String
            SYNC_STATE_CTAG = "ctag",
            SYNC_STATE_URL = "url";

    private final Bundle syncState = new Bundle();

    /**
     * Whether contact groups (LocalGroup resources) are included in query results for
     * {@link #getAll()}, {@link #getDeleted()}, {@link #getDirty()} and
     * {@link #getWithoutFileName()}.
     */
    public boolean includeGroups = true;


    public LocalAddressBook(Account account, ContentProviderClient provider) {
        super(account, provider, LocalGroup.Factory.INSTANCE, LocalContact.Factory.INSTANCE);
    }

    public LocalContact findContactByUID(String uid) throws ContactsStorageException, FileNotFoundException {
        LocalContact[] contacts = (LocalContact[])queryContacts(LocalContact.COLUMN_UID + "=?", new String[] { uid });
        if (contacts.length == 0)
            throw new FileNotFoundException();
        return contacts[0];
    }

    @Override
    public LocalResource[] getAll() throws ContactsStorageException {
        List<LocalResource> all = new LinkedList<>();
        Collections.addAll(all, (LocalResource[])queryContacts(null, null));
        if (includeGroups)
            Collections.addAll(all, (LocalResource[])queryGroups(null, null));
        return all.toArray(new LocalResource[all.size()]);
    }

    /**
     * Returns an array of local contacts/groups which have been deleted locally. (DELETED != 0).
     */
    @Override
    public LocalResource[] getDeleted() throws ContactsStorageException {
        List<LocalResource> deleted = new LinkedList<>();
        Collections.addAll(deleted, getDeletedContacts());
        if (includeGroups)
            Collections.addAll(deleted, getDeletedGroups());
        return deleted.toArray(new LocalResource[deleted.size()]);
    }

    /**
     * Queries all contacts with DIRTY flag and checks whether their data checksum has changed, i.e.
     * if they're "really dirty" (= data has changed, not only metadata, which is not hashed).
     * The DIRTY flag is removed from contacts which are not "really dirty", i.e. from contacts
     * whose contact data checksum has not changed.
     * @return number of "really dirty" contacts
     */
    public int verifyDirty() throws ContactsStorageException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            App.log.severe("verifyDirty() should not be called on Android <7");

        int reallyDirty = 0;
        for (LocalContact contact : getDirtyContacts()) {
            try {
                int lastHash = contact.getLastHashCode(),
                    currentHash = contact.dataHashCode();
                if (lastHash == currentHash) {
                    // hash is code still the same, contact is not "really dirty" (only metadata been have changed)
                    App.log.log(Level.FINE, "Contact data hash has not changed, resetting dirty flag", contact);
                    contact.resetDirty();
                } else {
                    App.log.log(Level.FINE, "Contact data has changed from hash " + lastHash + " to " + currentHash, contact);
                    reallyDirty++;
                }
            } catch(FileNotFoundException e) {
                throw new ContactsStorageException("Couldn't calculate hash code", e);
            }
        }

        if (includeGroups)
            reallyDirty += getDirtyGroups().length;

        return reallyDirty;
    }

    /**
     * Returns an array of local contacts/groups which have been changed locally (DIRTY != 0).
     */
    @Override
    public LocalResource[] getDirty() throws ContactsStorageException {
        List<LocalResource> dirty = new LinkedList<>();
        Collections.addAll(dirty, getDirtyContacts());
        if (includeGroups)
            Collections.addAll(dirty, getDirtyGroups());
        return dirty.toArray(new LocalResource[dirty.size()]);
    }

    /**
     * Returns an array of local contacts which don't have a file name yet.
     */
    @Override
    public LocalResource[] getWithoutFileName() throws ContactsStorageException {
        List<LocalResource> nameless = new LinkedList<>();
        Collections.addAll(nameless, (LocalContact[])queryContacts(AndroidContact.COLUMN_FILENAME + " IS NULL", null));
        if (includeGroups)
            Collections.addAll(nameless, (LocalGroup[])queryGroups(AndroidGroup.COLUMN_FILENAME + " IS NULL", null));
        return nameless.toArray(new LocalResource[nameless.size()]);
    }

    public void deleteAll() throws ContactsStorageException {
        try {
            provider.delete(syncAdapterURI(RawContacts.CONTENT_URI), null, null);
            provider.delete(syncAdapterURI(Groups.CONTENT_URI), null, null);
        } catch(RemoteException e) {
            throw new ContactsStorageException("Couldn't delete all local contacts and groups", e);
        }
    }


    public LocalContact[] getDeletedContacts() throws ContactsStorageException {
        return (LocalContact[])queryContacts(RawContacts.DELETED + "!= 0", null);
    }

    public LocalContact[] getDirtyContacts() throws ContactsStorageException {
        return (LocalContact[])queryContacts(RawContacts.DIRTY + "!= 0", null);
    }

    public LocalGroup[] getDeletedGroups() throws ContactsStorageException {
        return (LocalGroup[])queryGroups(Groups.DELETED + "!= 0", null);
    }

    public LocalGroup[] getDirtyGroups() throws ContactsStorageException {
        return (LocalGroup[])queryGroups(Groups.DIRTY + "!= 0", null);
    }


    /**
     * Finds the first group with the given title. If there is no group with this
     * title, a new group is created.
     * @param title     title of the group to look for
     * @return          id of the group with given title
     * @throws ContactsStorageException on contact provider errors
     */
    public long findOrCreateGroup(@NonNull String title) throws ContactsStorageException {
        try {
            @Cleanup Cursor cursor = provider.query(syncAdapterURI(Groups.CONTENT_URI),
                    new String[] { Groups._ID  },
                    Groups.TITLE + "=?", new String[] { title },
                    null);
            if (cursor != null && cursor.moveToNext())
                return cursor.getLong(0);

            ContentValues values = new ContentValues();
            values.put(Groups.TITLE, title);
            Uri uri = provider.insert(syncAdapterURI(Groups.CONTENT_URI), values);
            return ContentUris.parseId(uri);
        } catch(RemoteException e) {
            throw new ContactsStorageException("Couldn't find local contact group", e);
        }
    }

    public void removeEmptyGroups() throws ContactsStorageException {
        // find groups without members
        /** should be done using {@link Groups.SUMMARY_COUNT}, but it's not implemented in Android yet */
        for (LocalGroup group : (LocalGroup[])queryGroups(null, null))
            if (group.getMembers().length == 0) {
                App.log.log(Level.FINE, "Deleting group", group);
                group.delete();
            }
    }

    public void removeGroups() throws ContactsStorageException {
        try {
            provider.delete(syncAdapterURI(Groups.CONTENT_URI), null, null);
        } catch(RemoteException e) {
            throw new ContactsStorageException("Couldn't remove all groups", e);
        }
    }


    // SYNC STATE

    @SuppressWarnings("ParcelClassLoader,Recycle")
    protected void readSyncState() throws ContactsStorageException {
        @Cleanup("recycle") Parcel parcel = Parcel.obtain();
        byte[] raw = getSyncState();
        syncState.clear();
        if (raw != null) {
            parcel.unmarshall(raw, 0, raw.length);
            parcel.setDataPosition(0);
            syncState.putAll(parcel.readBundle());
        }
    }

    @SuppressWarnings("Recycle")
    protected void writeSyncState() throws ContactsStorageException {
        @Cleanup("recycle") Parcel parcel = Parcel.obtain();
        parcel.writeBundle(syncState);
        setSyncState(parcel.marshall());
    }

    public String getURL() throws ContactsStorageException {
        synchronized (syncState) {
            readSyncState();
            return syncState.getString(SYNC_STATE_URL);
        }
    }

    public void setURL(String url) throws ContactsStorageException {
        synchronized (syncState) {
            readSyncState();
            syncState.putString(SYNC_STATE_URL, url);
            writeSyncState();
        }
    }

    @Override
    public String getCTag() throws ContactsStorageException {
        synchronized (syncState) {
            readSyncState();
            return syncState.getString(SYNC_STATE_CTAG);
        }
    }

    @Override
    public void setCTag(String cTag) throws ContactsStorageException {
        synchronized (syncState) {
            readSyncState();
            syncState.putString(SYNC_STATE_CTAG, cTag);
            writeSyncState();
        }
    }


    // HELPERS

    public static void onRenameAccount(@NonNull ContentResolver resolver, @NonNull String oldName, @NonNull String newName) throws RemoteException {
        @Cleanup("release") ContentProviderClient client = resolver.acquireContentProviderClient(ContactsContract.AUTHORITY);
        if (client != null) {
            ContentValues values = new ContentValues(1);
            values.put(RawContacts.ACCOUNT_NAME, newName);
            client.update(RawContacts.CONTENT_URI, values, RawContacts.ACCOUNT_NAME + "=?", new String[]{oldName});
        }
    }

}
