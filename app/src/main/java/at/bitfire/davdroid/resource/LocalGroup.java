/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import android.content.ContentValues;
import android.provider.ContactsContract;

import at.bitfire.vcard4android.AndroidAddressBook;
import at.bitfire.vcard4android.AndroidGroup;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;

public class LocalGroup extends AndroidGroup {

    public LocalGroup(AndroidAddressBook addressBook, long id) {
        super(addressBook, id);
    }

    public LocalGroup(AndroidAddressBook addressBook, Contact contact) {
        super(addressBook, contact);
    }

    public void clearDirty() throws ContactsStorageException {
        ContentValues values = new ContentValues(1);
        values.put(ContactsContract.Groups.DIRTY, 0);
        update(values);
    }

}
