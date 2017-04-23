/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;

public class ContactsSyncAdapterService extends SyncAdapterService {

    @Override
    protected AbstractThreadedSyncAdapter syncAdapter() {
        return new ContactsSyncAdapter(this);
    }


	private static class ContactsSyncAdapter extends SyncAdapter {

        public ContactsSyncAdapter(Context context) {
            super(context);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            super.onPerformSync(account, extras, authority, provider, syncResult);

            SQLiteOpenHelper dbHelper = new ServiceDB.OpenHelper(getContext());
            try {
                LocalAddressBook addressBook = new LocalAddressBook(getContext(), account, provider);

                AccountSettings settings = new AccountSettings(getContext(), addressBook.getMainAccount());
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                    return;

                App.log.info("Synchronizing address book: "  + addressBook.getURL());
                App.log.info("Taking settings from: "  + addressBook.getMainAccount());

                ContactsSyncManager syncManager = new ContactsSyncManager(getContext(), account, settings, extras, authority, provider, syncResult, addressBook);
                syncManager.performSync();
            } catch(InvalidAccountException e) {
                App.log.log(Level.SEVERE, "Couldn't get account settings", e);
            } catch(ContactsStorageException e) {
                App.log.log(Level.SEVERE, "Couldn't prepare local address books", e);
            } finally {
                dbHelper.close();
            }

            App.log.info("Contacts sync complete");
        }

    }

}
