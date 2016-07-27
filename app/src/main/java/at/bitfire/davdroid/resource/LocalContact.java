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
import android.provider.ContactsContract.RawContacts.Data;
import android.support.annotation.NonNull;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;

import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.model.UnknownProperties;
import at.bitfire.vcard4android.AndroidAddressBook;
import at.bitfire.vcard4android.AndroidContact;
import at.bitfire.vcard4android.AndroidContactFactory;
import at.bitfire.vcard4android.BatchOperation;
import at.bitfire.vcard4android.CachedGroupMembership;
import at.bitfire.vcard4android.Contact;
import at.bitfire.vcard4android.ContactsStorageException;
import ezvcard.Ezvcard;

public class LocalContact extends AndroidContact implements LocalResource {
    static {
        Contact.productID = "+//IDN bitfire.at//DAVdroid/" + BuildConfig.VERSION_NAME + " vcard4android ez-vcard/" + Ezvcard.VERSION;
    }

    protected final Set<Long>
            cachedGroupMemberships = new HashSet<>(),
            groupMemberships = new HashSet<>();


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


    @Override
    protected void populateData(String mimeType, ContentValues row) {
        switch (mimeType) {
            case CachedGroupMembership.CONTENT_ITEM_TYPE:
                cachedGroupMemberships.add(row.getAsLong(CachedGroupMembership.GROUP_ID));
                break;
            case GroupMembership.CONTENT_ITEM_TYPE:
                groupMemberships.add(row.getAsLong(GroupMembership.GROUP_ROW_ID));
                break;
            case UnknownProperties.CONTENT_ITEM_TYPE:
                contact.unknownProperties = row.getAsString(UnknownProperties.UNKNOWN_PROPERTIES);
                break;
        }
    }

    @Override
    protected void insertDataRows(BatchOperation batch) throws ContactsStorageException {
        super.insertDataRows(batch);

        if (contact.unknownProperties != null) {
            final BatchOperation.Operation op;
            final ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(dataSyncURI());
            if (id == null) {
                op = new BatchOperation.Operation(builder, UnknownProperties.RAW_CONTACT_ID, 0);
            } else {
                op = new BatchOperation.Operation(builder);
                builder.withValue(UnknownProperties.RAW_CONTACT_ID, id);
            }
            builder .withValue(UnknownProperties.MIMETYPE, UnknownProperties.CONTENT_ITEM_TYPE)
                    .withValue(UnknownProperties.UNKNOWN_PROPERTIES, contact.unknownProperties);
            batch.enqueue(op);
        }

    }


    public void addToGroup(BatchOperation batch, long groupID) {
        assertID();
        batch.enqueue(new BatchOperation.Operation(
                ContentProviderOperation.newInsert(dataSyncURI())
                        .withValue(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(GroupMembership.RAW_CONTACT_ID, id)
                        .withValue(GroupMembership.GROUP_ROW_ID, groupID)
        ));

        batch.enqueue(new BatchOperation.Operation(
                ContentProviderOperation.newInsert(dataSyncURI())
                        .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(CachedGroupMembership.RAW_CONTACT_ID, id)
                        .withValue(CachedGroupMembership.GROUP_ID, groupID)
                        .withYieldAllowed(true)
        ));
    }

    public void removeGroupMemberships(BatchOperation batch) {
        assertID();
        batch.enqueue(new BatchOperation.Operation(
                ContentProviderOperation.newDelete(dataSyncURI())
                        .withSelection(
                                Data.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + " IN (?,?)",
                                new String[] { String.valueOf(id), GroupMembership.CONTENT_ITEM_TYPE, CachedGroupMembership.CONTENT_ITEM_TYPE }
                        )
                        .withYieldAllowed(true)
        ));
    }

    /**
     * Returns the IDs of all groups the contact was member of (cached memberships).
     * Cached memberships are kept in sync with memberships by DAVdroid and are used to determine
     * whether a membership has been deleted/added when a raw contact is dirty.
     * @return set of {@link GroupMembership#GROUP_ROW_ID} (may be empty)
     * @throws ContactsStorageException   on contact provider errors
     * @throws FileNotFoundException      if the current contact can't be found
     */
    @NonNull
    public Set<Long> getCachedGroupMemberships() throws ContactsStorageException, FileNotFoundException {
        getContact();
        return cachedGroupMemberships;
    }

    /**
     * Returns the IDs of all groups the contact is member of.
     * @return set of {@link GroupMembership#GROUP_ROW_ID}s (may be empty)
     * @throws ContactsStorageException   on contact provider errors
     * @throws FileNotFoundException      if the current contact can't be found
     */
    @NonNull
    public Set<Long> getGroupMemberships() throws ContactsStorageException, FileNotFoundException {
        getContact();
        return groupMemberships;
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

        @Override
        public LocalContact[] newArray(int size) {
            return new LocalContact[size];
        }

    }

}
