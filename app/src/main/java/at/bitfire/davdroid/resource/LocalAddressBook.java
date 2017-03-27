/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.DavUtils;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.vcard4android.AndroidAddressBook;
import at.bitfire.vcard4android.AndroidContact;
import at.bitfire.vcard4android.AndroidGroup;
import at.bitfire.vcard4android.CachedGroupMembership;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;


public class LocalAddressBook extends AndroidAddressBook implements LocalCollection {

    protected static final String
            USER_DATA_MAIN_ACCOUNT_TYPE = "real_account_type",
            USER_DATA_MAIN_ACCOUNT_NAME = "real_account_name",
            USER_DATA_URL = "url",
            USER_DATA_CTAG = "ctag";

    protected final Context context;
    private final Bundle syncState = new Bundle();

    /**
     * Whether contact groups (LocalGroup resources) are included in query results for
     * {@link #getAll()}, {@link #getDeleted()}, {@link #getDirty()} and
     * {@link #getWithoutFileName()}.
     */
    public boolean includeGroups = true;


    public static LocalAddressBook[] find(@NonNull Context context, @NonNull ContentProviderClient provider, @Nullable Account mainAccount) throws ContactsStorageException {
        AccountManager accountManager = AccountManager.get(context);

        List<LocalAddressBook> result = new LinkedList<>();
        for (Account account : accountManager.getAccountsByType(App.getAddressBookAccountType())) {
            LocalAddressBook addressBook = new LocalAddressBook(context, account, provider);
            if (mainAccount == null || addressBook.getMainAccount().equals(mainAccount))
                result.add(addressBook);
        }

        return result.toArray(new LocalAddressBook[result.size()]);
    }

    public static LocalAddressBook create(@NonNull Context context, @NonNull ContentProviderClient provider, @NonNull Account mainAccount, @NonNull CollectionInfo info) throws ContactsStorageException {
        AccountManager accountManager = AccountManager.get(context);

        Account account = new Account(accountName(mainAccount, info), App.getAddressBookAccountType());
        if (!accountManager.addAccountExplicitly(account, null, initialUserData(mainAccount, info.url)))
            throw new ContactsStorageException("Couldn't create address book account");

        LocalAddressBook addressBook = new LocalAddressBook(context, account, provider);
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
        return addressBook;
    }

    public void update(@NonNull CollectionInfo info) throws AuthenticatorException, OperationCanceledException, IOException, ContactsStorageException {
        final String newAccountName = accountName(getMainAccount(), info);
        if (!account.name.equals(newAccountName) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final AccountManager accountManager = AccountManager.get(context);
            AccountManagerFuture<Account> future = accountManager.renameAccount(account, newAccountName, new AccountManagerCallback<Account>() {
                @Override
                public void run(AccountManagerFuture<Account> future) {
                    try {
                        // update raw contacts to new account name
                        if (provider != null) {
                            ContentValues values = new ContentValues(1);
                            values.put(RawContacts.ACCOUNT_NAME, newAccountName);
                            provider.update(syncAdapterURI(RawContacts.CONTENT_URI), values, RawContacts.ACCOUNT_NAME + "=?", new String[] { account.name });
                        }
                    } catch(RemoteException e) {
                        App.log.log(Level.WARNING, "Couldn't re-assign contacts to new account name", e);
                    }
                }
            }, null);
            account = future.getResult();
        }

        // make sure it will still be synchronized when contacts are updated
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
    }

    public void delete() {
        AccountManager accountManager = AccountManager.get(context);
        accountManager.removeAccount(account, null, null);
    }


    public LocalAddressBook(Context context, Account account, ContentProviderClient provider) {
        super(account, provider, LocalGroup.Factory.INSTANCE, LocalContact.Factory.INSTANCE);
        this.context = context;
    }

    @NonNull
    public LocalContact findContactByUID(String uid) throws ContactsStorageException, FileNotFoundException {
        LocalContact[] contacts = (LocalContact[])queryContacts(LocalContact.COLUMN_UID + "=?", new String[] { uid });
        if (contacts.length == 0)
            throw new FileNotFoundException();
        return contacts[0];
    }

    @Override
    @NonNull
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
    @NonNull
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
    @NonNull
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
    @NonNull
    public LocalResource[] getWithoutFileName() throws ContactsStorageException {
        List<LocalResource> nameless = new LinkedList<>();
        Collections.addAll(nameless, (LocalContact[])queryContacts(AndroidContact.COLUMN_FILENAME + " IS NULL", null));
        if (includeGroups)
            Collections.addAll(nameless, (LocalGroup[])queryGroups(AndroidGroup.COLUMN_FILENAME + " IS NULL", null));
        return nameless.toArray(new LocalResource[nameless.size()]);
    }


    @NonNull
    public LocalContact[] getDeletedContacts() throws ContactsStorageException {
        return (LocalContact[])queryContacts(RawContacts.DELETED + "!= 0", null);
    }

    @NonNull
    public LocalContact[] getDirtyContacts() throws ContactsStorageException {
        return (LocalContact[])queryContacts(RawContacts.DIRTY + "!= 0", null);
    }

    @NonNull
    public LocalGroup[] getDeletedGroups() throws ContactsStorageException {
        return (LocalGroup[])queryGroups(Groups.DELETED + "!= 0", null);
    }

    @NonNull
    public LocalGroup[] getDirtyGroups() throws ContactsStorageException {
        return (LocalGroup[])queryGroups(Groups.DIRTY + "!= 0", null);
    }

    @NonNull LocalContact[] getByGroupMembership(long groupID) throws ContactsStorageException {
        try {
            @Cleanup Cursor cursor = provider.query(syncAdapterURI(ContactsContract.Data.CONTENT_URI),
                    new String[] { RawContacts.Data.RAW_CONTACT_ID },
                    "(" + GroupMembership.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?) OR (" + CachedGroupMembership.MIMETYPE + "=? AND " + CachedGroupMembership.GROUP_ID + "=?)",
                    new String[] { GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupID), CachedGroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupID) },
                    null);

            Set<Long> ids = new HashSet<>();
            while (cursor != null && cursor.moveToNext())
                ids.add(cursor.getLong(0));

            LocalContact[] contacts = new LocalContact[ids.size()];
            int i = 0;
            for (Long id : ids)
                contacts[i++] = new LocalContact(this, id, null, null);
            return contacts;
        } catch (RemoteException e) {
            throw new ContactsStorageException("Couldn't query contacts", e);
        }
    }


    public void deleteAll() throws ContactsStorageException {
        try {
            provider.delete(syncAdapterURI(RawContacts.CONTENT_URI), null, null);
            provider.delete(syncAdapterURI(Groups.CONTENT_URI), null, null);
        } catch(RemoteException e) {
            throw new ContactsStorageException("Couldn't delete all local contacts and groups", e);
        }
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


    // SETTINGS

    public static Bundle initialUserData(@NonNull Account mainAccount, @NonNull String url) {
        Bundle bundle = new Bundle(3);
        bundle.putString(USER_DATA_MAIN_ACCOUNT_NAME, mainAccount.name);
        bundle.putString(USER_DATA_MAIN_ACCOUNT_TYPE, mainAccount.type);
        bundle.putString(USER_DATA_URL, url);
        return bundle;
    }

    public Account getMainAccount() throws ContactsStorageException {
        AccountManager accountManager = AccountManager.get(context);
        String  name = accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_NAME),
                type = accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE);
        if (name != null && type != null)
            return new Account(name, type);
        else
            throw new ContactsStorageException("Address book doesn't exist anymore");
    }

    public void setMainAccount(@NonNull Account mainAccount) throws ContactsStorageException {
        AccountManager accountManager = AccountManager.get(context);
        accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_NAME, mainAccount.name);
        accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE, mainAccount.type);
    }

    public String getURL() throws ContactsStorageException {
        AccountManager accountManager = AccountManager.get(context);
        return accountManager.getUserData(account, USER_DATA_URL);
    }

    public void setURL(String url) throws ContactsStorageException {
        AccountManager accountManager = AccountManager.get(context);
        accountManager.setUserData(account, USER_DATA_URL, url);
    }

    @Override
    public String getCTag() throws ContactsStorageException {
        AccountManager accountManager = AccountManager.get(context);
        return accountManager.getUserData(account, USER_DATA_CTAG);
    }

    @Override
    public void setCTag(String cTag) throws ContactsStorageException {
        AccountManager accountManager = AccountManager.get(context);
        accountManager.setUserData(account, USER_DATA_CTAG, cTag);
    }


    // HELPERS

    public static String accountName(@NonNull Account mainAccount, @NonNull CollectionInfo info) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(info.url.hashCode());
        String hash = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP | Base64.NO_PADDING);

        StringBuilder sb = new StringBuilder(!TextUtils.isEmpty(info.displayName) ? info.displayName : DavUtils.lastSegmentOfUrl(info.url));
        sb      .append(" (")
                .append(mainAccount.name)
                .append(" ")
                .append(hash)
                .append(")");
        return sb.toString();
    }

}
