/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import at.bitfire.vcard4android.AndroidAddressBook;
import at.bitfire.vcard4android.AndroidContact;
import at.bitfire.vcard4android.AndroidContactFactory;
import at.bitfire.vcard4android.Contact;

public class LocalContact extends AndroidContact {

    protected LocalContact(AndroidAddressBook addressBook, long id) {
        super(addressBook, id);
    }

    public LocalContact(AndroidAddressBook addressBook, Contact contact) {
        super(addressBook, contact);
    }


    static class Factory extends AndroidContactFactory {
        static final Factory INSTANCE = new Factory();

        @Override
        public LocalContact newInstance(AndroidAddressBook addressBook, long id) {
            return new LocalContact(addressBook, id);
        }

        @Override
        public LocalContact newInstance(AndroidAddressBook addressBook, Contact contact) {
            return new LocalContact(addressBook, contact);
        }

        public LocalContact[] newArray(int size) {
            return new LocalContact[size];
        }

    }

}
