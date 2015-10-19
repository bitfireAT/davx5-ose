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
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcel;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;

import java.util.LinkedList;
import java.util.List;

import at.bitfire.davdroid.Constants;
import at.bitfire.vcard4android.AndroidAddressBook;
import at.bitfire.vcard4android.AndroidContact;
import at.bitfire.vcard4android.AndroidGroupFactory;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;


public class LocalAddressBook extends AndroidAddressBook implements LocalCollection {

    protected static final String
            SYNC_STATE_CTAG = "ctag",
            SYNC_STATE_URL = "url";

    private Bundle syncState = new Bundle();


    public LocalAddressBook(Account account, ContentProviderClient provider) {
        super(account, provider, AndroidGroupFactory.INSTANCE, LocalContact.Factory.INSTANCE);
    }


    /**
     * Returns an array of local contacts, excluding those which have been modified locally (and not uploaded yet).
     */
    @Override
    public LocalContact[] getAll() throws ContactsStorageException {
        return (LocalContact[])queryContacts(null, null);
    }

    /**
     * Returns an array of local contacts which have been deleted locally. (DELETED != 0).
     */
    @Override
    public LocalContact[] getDeleted() throws ContactsStorageException {
        return (LocalContact[])queryContacts(RawContacts.DELETED + "!=0", null);
    }

    /**
     * Returns an array of local contacts which have been changed locally (DIRTY != 0).
     */
    @Override
    public LocalContact[] getDirty() throws ContactsStorageException {
        return (LocalContact[])queryContacts(RawContacts.DIRTY + "!=0", null);
    }

    /**
     * Returns an array of local contacts which don't have a file name yet.
     */
    @Override
    public LocalContact[] getWithoutFileName() throws ContactsStorageException {
        return (LocalContact[])queryContacts(AndroidContact.COLUMN_FILENAME + " IS NULL", null);
    }


    // GROUPS

    /**
     * Finds the first group with the given title.
     * @param displayName   title of the group to look for
     * @return              group with given title, or null if none
     */
    @SuppressWarnings("Recycle")
    public LocalGroup findGroupByTitle(String displayName) throws ContactsStorageException {
        try {
            @Cleanup Cursor cursor = provider.query(syncAdapterURI(Groups.CONTENT_URI),
                    new String[] { Groups._ID },
                    ContactsContract.Groups.TITLE + "=?", new String[] { displayName }, null);
            if (cursor != null && cursor.moveToNext())
                return new LocalGroup(this, cursor.getLong(0));
        } catch (RemoteException e) {
            throw new ContactsStorageException("Couldn't find local contact group", e);
        }
        return null;
    }

    @SuppressWarnings("Recycle")
    public LocalGroup[] getDeletedGroups() throws ContactsStorageException {
        List<LocalGroup> groups = new LinkedList<>();
        try {
            @Cleanup Cursor cursor = provider.query(syncAdapterURI(Groups.CONTENT_URI),
                    new String[] { Groups._ID },
                    Groups.DELETED + "!=0", null, null);
            while (cursor != null && cursor.moveToNext())
                groups.add(new LocalGroup(this, cursor.getLong(0)));
        } catch (RemoteException e) {
            throw new ContactsStorageException("Couldn't query deleted groups", e);
        }
        return groups.toArray(new LocalGroup[groups.size()]);
    }

    @SuppressWarnings("Recycle")
    public LocalGroup[] getDirtyGroups() throws ContactsStorageException {
        List<LocalGroup> groups = new LinkedList<>();
        try {
            @Cleanup Cursor cursor = provider.query(syncAdapterURI(Groups.CONTENT_URI),
                    new String[] { Groups._ID },
                    Groups.DIRTY + "!=0", null, null);
            while (cursor != null && cursor.moveToNext())
                groups.add(new LocalGroup(this, cursor.getLong(0)));
        } catch (RemoteException e) {
            throw new ContactsStorageException("Couldn't query dirty groups", e);
        }
        return groups.toArray(new LocalGroup[groups.size()]);
    }

    @SuppressWarnings("Recycle")
    public void markMembersDirty(long groupId) throws ContactsStorageException {
        ContentValues dirty = new ContentValues(1);
        dirty.put(RawContacts.DIRTY, 1);
        try {
            // query all GroupMemberships of this groupId, mark every corresponding raw contact as DIRTY
            @Cleanup Cursor cursor = provider.query(syncAdapterURI(Data.CONTENT_URI),
                    new String[] { GroupMembership.RAW_CONTACT_ID },
                    Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                    new String[] { GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupId) }, null);
            while (cursor != null && cursor.moveToNext()) {
                long id = cursor.getLong(0);
                Constants.log.debug("Marking raw contact #" + id + " as dirty");
                provider.update(syncAdapterURI(ContentUris.withAppendedId(RawContacts.CONTENT_URI, id)), dirty, null, null);
            }
        } catch (RemoteException e) {
            throw new ContactsStorageException("Couldn't query dirty groups", e);
        }
    }


    // SYNC STATE

    @SuppressWarnings("Recycle")
    protected void readSyncState() throws ContactsStorageException {
        @Cleanup("recycle") Parcel parcel = Parcel.obtain();
        byte[] raw = getSyncState();
        if (raw != null) {
            parcel.unmarshall(raw, 0, raw.length);
            parcel.setDataPosition(0);
            syncState = parcel.readBundle();
        } else
            syncState.clear();
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

}
