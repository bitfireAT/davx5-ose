/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import android.content.ContentValues;
import android.os.RemoteException;
import android.provider.ContactsContract;

import at.bitfire.davdroid.BuildConfig;
import at.bitfire.vcard4android.AndroidAddressBook;
import at.bitfire.vcard4android.AndroidContact;
import at.bitfire.vcard4android.AndroidContactFactory;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;
import ezvcard.Ezvcard;

public class LocalContact extends AndroidContact {
    static {
        Contact.productID = "+//IDN bitfire.at//DAVdroid/" + BuildConfig.VERSION_NAME + " ez-vcard/" + Ezvcard.VERSION;
    }

    protected LocalContact(AndroidAddressBook addressBook, long id, String fileName, String eTag) {
        super(addressBook, id, fileName, eTag);
    }

    public LocalContact(AndroidAddressBook addressBook, Contact contact, String fileName, String eTag) {
        super(addressBook, contact, fileName, eTag);
    }

    public void clearDirty(String eTag) throws ContactsStorageException {
        try {
            ContentValues values = new ContentValues(1);
            values.put(COLUMN_ETAG, eTag);
            values.put(ContactsContract.RawContacts.DIRTY, 0);
            addressBook.provider.update(rawContactSyncURI(), values, null, null);
        } catch (RemoteException e) {
            throw new ContactsStorageException("Couldn't clear dirty flag", e);
        }
    }

    public void updateUID(String uid) throws ContactsStorageException {
        try {
            ContentValues values = new ContentValues(1);
            values.put(COLUMN_UID, uid);
            addressBook.provider.update(rawContactSyncURI(), values, null, null);
        } catch (RemoteException e) {
            throw new ContactsStorageException("Couldn't update UID", e);
        }
    }


    static class Factory extends AndroidContactFactory {
        static final Factory INSTANCE = new Factory();

        @Override
        public LocalContact newInstance(AndroidAddressBook addressBook, long id, String fileName, String eTag) {
            return new LocalContact(addressBook, id, fileName, eTag);
        }

        @Override
        public LocalContact newInstance(AndroidAddressBook addressBook, Contact contact, String fileName, String eTag) {
            return new LocalContact(addressBook, contact, fileName, eTag);
        }

        public LocalContact[] newArray(int size) {
            return new LocalContact[size];
        }

    }

}
