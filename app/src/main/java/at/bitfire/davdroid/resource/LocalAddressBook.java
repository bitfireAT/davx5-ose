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
import android.os.Bundle;
import android.os.Parcel;
import android.provider.ContactsContract;

import at.bitfire.davdroid.Constants;
import at.bitfire.vcard4android.AndroidAddressBook;
import at.bitfire.vcard4android.AndroidContact;
import at.bitfire.vcard4android.AndroidContactFactory;
import at.bitfire.vcard4android.AndroidGroupFactory;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;
import lombok.Synchronized;


public class LocalAddressBook extends AndroidAddressBook implements LocalCollection {

    protected static final String SYNC_STATE_CTAG = "ctag";

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
        return (LocalContact[])queryContacts(ContactsContract.RawContacts.DELETED + "!=0", null);
    }

    /**
     * Returns an array of local contacts which have been changed locally (DIRTY != 0).
     */
    @Override
    public LocalContact[] getDirty() throws ContactsStorageException {
        return (LocalContact[])queryContacts(ContactsContract.RawContacts.DIRTY + "!=0", null);
    }

    /**
     * Returns an array of local contacts which don't have a file name yet.
     */
    @Override
    public LocalContact[] getWithoutFileName() throws ContactsStorageException {
        return (LocalContact[])queryContacts(AndroidContact.COLUMN_FILENAME + " IS NULL", null);
    }


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

            // write sync state bundle
            @Cleanup("recycle") Parcel parcel = Parcel.obtain();
            parcel.writeBundle(syncState);
            setSyncState(parcel.marshall());
        }
    }

}
