/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;

import java.io.FileNotFoundException;

import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.Constants;
import at.bitfire.vcard4android.AndroidAddressBook;
import at.bitfire.vcard4android.AndroidContact;
import at.bitfire.vcard4android.AndroidContactFactory;
import at.bitfire.vcard4android.BatchOperation;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;
import ezvcard.Ezvcard;

public class LocalContact extends AndroidContact implements LocalResource {
    static {
        Contact.productID = "+//IDN bitfire.at//DAVdroid/" + BuildConfig.VERSION_NAME + " vcard4android ez-vcard/" + Ezvcard.VERSION;
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
            values.put(ContactsContract.RawContacts.DIRTY, 0);
            values.put(COLUMN_ETAG, eTag);
            addressBook.provider.update(rawContactSyncURI(), values, null, null);

            this.eTag = eTag;
        } catch (RemoteException e) {
            throw new ContactsStorageException("Couldn't clear dirty flag", e);
        }
    }

    public void updateFileNameAndUID(String uid) throws ContactsStorageException {
        try {
            String newFileName = uid + ".vcf";

            ContentValues values = new ContentValues(2);
            values.put(COLUMN_FILENAME, newFileName);
            values.put(COLUMN_UID, uid);
            addressBook.provider.update(rawContactSyncURI(), values, null, null);

            fileName = newFileName;
        } catch (RemoteException e) {
            throw new ContactsStorageException("Couldn't update UID", e);
        }
    }


    // group support

    @Override
    protected void populateGroupMembership(ContentValues row) {
        if (row.containsKey(GroupMembership.GROUP_ROW_ID)) {
            long groupId = row.getAsLong(GroupMembership.GROUP_ROW_ID);

            // fetch group
            LocalGroup group = new LocalGroup(addressBook, groupId);
            try {
                Contact groupInfo = group.getContact();

                // add to CATEGORIES
                contact.getCategories().add(groupInfo.displayName);
            } catch (FileNotFoundException|ContactsStorageException e) {
                Constants.log.warn("Couldn't find assigned group #" + groupId + ", ignoring membership", e);
            }
        }
    }

    @Override
    protected void insertGroupMemberships(BatchOperation batch) throws ContactsStorageException {
        for (String category : contact.getCategories()) {
            // Is there already a category with this display name?
            LocalGroup group = ((LocalAddressBook)addressBook).findGroupByTitle(category);

            if (group == null) {
                // no, we have to create the group before inserting the membership

                Contact groupInfo = new Contact();
                groupInfo.displayName = category;
                group = new LocalGroup(addressBook, groupInfo);
                group.create();
            }

            Long groupId = group.getId();
            if (groupId != null) {
                ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(dataSyncURI());
                if (id == null)
                    builder.withValueBackReference(GroupMembership.RAW_CONTACT_ID, 0);
                else
                    builder.withValue(GroupMembership.RAW_CONTACT_ID, id);
                builder .withValue(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(GroupMembership.GROUP_ROW_ID, groupId);
                batch.enqueue(builder.build());
            }
        }
    }


    // factory

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
