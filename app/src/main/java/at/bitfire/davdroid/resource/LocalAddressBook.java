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
import android.provider.ContactsContract;

import at.bitfire.davdroid.Constants;
import at.bitfire.vcard4android.AndroidAddressBook;
import at.bitfire.vcard4android.AndroidContact;
import at.bitfire.vcard4android.AndroidContactFactory;
import at.bitfire.vcard4android.AndroidGroupFactory;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;


public class LocalAddressBook extends AndroidAddressBook {

    public LocalAddressBook(Account account, ContentProviderClient provider) {
        super(account, provider, AndroidGroupFactory.INSTANCE, LocalContact.Factory.INSTANCE);
    }

    public LocalContact[] getAll() throws ContactsStorageException {
        LocalContact contacts[] = (LocalContact[])queryContacts(null, null);
        return contacts;
    }

    /**
     * Returns an array of local contacts which have been deleted locally. (DELETED != 0).
     */
    public LocalContact[] getDeleted() throws ContactsStorageException {
        return (LocalContact[])queryContacts(ContactsContract.RawContacts.DELETED + " != 0", null);
    }

    /**
     * Returns an array of local contacts which have been changed locally (DIRTY != 0).
     */
    public LocalContact[] getDirty() throws ContactsStorageException {
        return (LocalContact[])queryContacts(ContactsContract.RawContacts.DIRTY + " != 0", null);
    }

    /**
     * Returns an array of local contacts which don't have a file name yet.
     */
    public LocalContact[] getWithoutFileName() throws ContactsStorageException {
        return (LocalContact[])queryContacts(AndroidContact.COLUMN_FILENAME + " IS NULL", null);
    }

}
